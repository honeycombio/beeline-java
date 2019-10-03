package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.spring.autoconfig.BeelineProperties;
import io.honeycomb.beeline.spring.utils.InstrumentationConstants;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.propagation.HttpServerPropagator;
import io.honeycomb.beeline.tracing.propagation.HttpServerRequestSpanCustomizer;
import io.honeycomb.libhoney.utils.Assert;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.WEBMVC_INSTRUMENTATION_NAME;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_ASYNC_DISPATCH_FIELD;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_DISPATCHER_TYPE_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;

public class BeelineServletFilter implements Filter, BeelineInstrumentation {
    private static final String ASYNC_TIMEOUT_ERROR = "AsyncTimeout";

    private final List<String> includePaths;
    private final List<String> excludePaths;
    private final AntPathMatcher pathMatcher;
    private final HttpServerPropagator httpServerPropagator;
    private final HttpServerRequestSpanCustomizer spanRequestFieldsCustomizer;
    private final Beeline beeline;

    public BeelineServletFilter(final BeelineProperties beelineProperties,
                                final Beeline beeline,
                                final List<String> includePaths,
                                final List<String> excludePaths) {
        Assert.notNull(beelineProperties, "Validation failed: beelineProperties must not be null");
        Assert.notNull(beeline, "Validation failed: beeline must not be null");

        this.includePaths = new ArrayList<>(includePaths);
        this.excludePaths = new ArrayList<>(excludePaths);
        this.pathMatcher = new AntPathMatcher();
        this.beeline = beeline;
        this.spanRequestFieldsCustomizer = new HttpServerRequestSpanCustomizer();
        this.httpServerPropagator = new HttpServerPropagator(beeline,
                                                            beelineProperties.getServiceName(),
                                                            this::extractRootSpanName);
    }

    @Override
    public String getName() {
        return WEBMVC_INSTRUMENTATION_NAME;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        // no init needed
    }

    @Override
    public void destroy() {
        // no destroy needed
    }

    @Override
    public void doFilter(final ServletRequest request,
                         final ServletResponse response,
                         final FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        final ServletServerHttpRequest wrappedRequest = new ServletServerHttpRequest(httpServletRequest);

        switch (request.getDispatcherType()) {
            case REQUEST:
                if (pathMatches(wrappedRequest)) {
                    initializeRootSpan(wrappedRequest);
                }
                break;
            case FORWARD:
            case ERROR:
            case INCLUDE: {
                initializeRedispatchSpan(wrappedRequest);
                break;
            }
            case ASYNC:
                break;
        }

        // keep a reference to the span that was active when the filter was invoked
        final Span currentFilterSpan = beeline.getTracer().getActiveSpan();
        Exception exception = null;
        try {
            chain.doFilter(httpServletRequest, httpServletResponse);
        } catch (final Exception e) {
            exception = e;
            throw e;
        } finally {
            final Span detachedSpan = beeline.getTracer().popSpan(currentFilterSpan);
            if (httpServletRequest.isAsyncStarted()) {
                detachedSpan.addField(SPRING_ASYNC_DISPATCH_FIELD, true);
                final AsyncListener listener = new TraceListener(detachedSpan, httpServerPropagator);
                httpServletRequest.getAsyncContext().addListener(listener, httpServletRequest, httpServletResponse);
            } else {
                handleResponse(httpServletResponse, exception, detachedSpan);
            }
        }
    }

    protected boolean pathMatches(final ServletServerHttpRequest request) {
        final String maybePath = UriComponentsBuilder.fromUri(request.getURI()).build().getPath();
        final String path = maybePath == null ? "/" : maybePath;
        if (!this.excludePaths.isEmpty()) {
            for (final String pattern : this.excludePaths) {
                if (pathMatcher.match(pattern, path)) {
                    return false;
                }
            }
        }
        if (this.includePaths.isEmpty()) {
            return true;
        } else {
            for (final String pattern : this.includePaths) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void initializeRedispatchSpan(final ServletServerHttpRequest request) {
        final String spanName = InstrumentationConstants.FILTER_SPAN_NAME_PREFIX +
                                getDispatcherTypeName(request).toLowerCase(Locale.ENGLISH);
        final Span childSpan = beeline.getTracer().startChildSpan(spanName);
        childSpan
            .addField(SPRING_DISPATCHER_TYPE_FIELD, getDispatcherTypeName(request));
        spanRequestFieldsCustomizer.customize(childSpan, new HttpServerRequestAdapter(request));
    }

    protected void handleResponse(final HttpServletResponse httpServletResponse,
                                  final Throwable throwable,
                                  final Span currentSpan) {
        finishSpan(httpServerPropagator, httpServletResponse, throwable, currentSpan);
    }

    protected void initializeRootSpan(final ServletServerHttpRequest request) {
        final Span rootSpan = httpServerPropagator.startPropagation(new HttpServerRequestAdapter(request));
        rootSpan.addField(SPRING_DISPATCHER_TYPE_FIELD, getDispatcherTypeName(request));
    }

    protected String extractRootSpanName(final io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter
                                             httpRequest) {
        return InstrumentationConstants.FILTER_SPAN_NAME_PREFIX +
            httpRequest.getMethod().toLowerCase(Locale.ENGLISH);
    }

    private String getDispatcherTypeName(final ServletServerHttpRequest request) {
        return request.getServletRequest().getDispatcherType().name();
    }

    private static void finishSpan(final HttpServerPropagator httpServerPropagator,
                                   final HttpServletResponse response,
                                   final Throwable throwable,
                                   final Span currentSpan) {
        httpServerPropagator.endPropagation(new HttpServerResponseAdapter(response), throwable, currentSpan);
    }

    public static class TraceListener implements AsyncListener {
        private final Span detachedSpan;
        private final HttpServerPropagator httpServerPropagator;
        private volatile boolean completed;

        public TraceListener(final Span detachedSpan, final HttpServerPropagator httpServerPropagator) {
            this.detachedSpan = detachedSpan;
            this.httpServerPropagator = httpServerPropagator;
        }

        @Override
        public void onComplete(final AsyncEvent event) {
            if (completed) {
                return;
            }
            finish(event);
        }

        @Override
        public void onTimeout(final AsyncEvent event) {
            if (completed) {
                return;
            }
            final String errorDetail = "Async request timed out after " + event.getAsyncContext().getTimeout() + " ms";
            detachedSpan
                .addField(REQUEST_ERROR_FIELD, ASYNC_TIMEOUT_ERROR)
                .addField(REQUEST_ERROR_DETAIL_FIELD, errorDetail);
            finish(event);
        }

        @Override
        public void onError(final AsyncEvent event) {
            if (completed) {
                return;
            }
            finish(event);
        }

        @Override
        public void onStartAsync(final AsyncEvent event) {
            final AsyncContext eventAsyncContext = event.getAsyncContext();
            if (eventAsyncContext != null) {
                eventAsyncContext.addListener(this, event.getSuppliedRequest(), event.getSuppliedResponse());
            }
        }

        private void finish(final AsyncEvent event) {
            final HttpServletResponse suppliedResponse = (HttpServletResponse) event.getSuppliedResponse();
            finishSpan(httpServerPropagator, suppliedResponse, event.getThrowable(), detachedSpan);
            completed = true;
        }

    }

    public static class HttpServerRequestAdapter
        implements io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter {

        private final ServletServerHttpRequest request;
        private final UriComponents uriComponents;

        public HttpServerRequestAdapter(final ServletServerHttpRequest request) {
            this.request = request;
            this.uriComponents = UriComponentsBuilder.fromUri(request.getURI()).build();
        }

        @Override
        public String getMethod() {
            return request.getMethodValue();
        }

        @Override
        public Optional<String> getPath() {
            return Optional.ofNullable(uriComponents.getPath());
        }

        @Override
        public Optional<String> getFirstHeader(String name) {
            return Optional.ofNullable(request.getHeaders().getFirst(name));
        }

        @Override
        public Optional<String> getScheme() {
            return Optional.ofNullable(uriComponents.getScheme());
        }

        @Override
        public Optional<String> getHost() {
            return Optional.ofNullable(uriComponents.getHost());
        }

        @Override
        public String getHttpVersion() {
            return request.getServletRequest().getProtocol();
        }

        @Override
        public boolean isSecure() {
            return request.getServletRequest().isSecure();
        }

        @Override
        public String getRemoteAddress() {
            return request.getServletRequest().getRemoteAddr();
        }

        @Override
        public Map<String, List<String>> getQueryParams() {
            return uriComponents.getQueryParams();
        }

        @Override
        public int getContentLength() {
            return request.getServletRequest().getContentLength();
        }
    }

    public static class HttpServerResponseAdapter implements io.honeycomb.beeline.tracing.propagation.HttpServerResponseAdapter {
        private final HttpServletResponse response;

        public HttpServerResponseAdapter(final HttpServletResponse response) {
            this.response = response;
        }

        @Override
        public int getStatus() {
            return response.getStatus();
        }

        @Override
        public Optional<String> getFirstHeader(String name) {
            return Optional.ofNullable(response.getHeader(name));
        }
    }
}
