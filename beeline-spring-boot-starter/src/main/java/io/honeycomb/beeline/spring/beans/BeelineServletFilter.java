package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.spring.autoconfig.BeelineProperties;
import io.honeycomb.beeline.spring.utils.BeelineUtils;
import io.honeycomb.beeline.spring.utils.InstrumentationConstants;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.libhoney.utils.Assert;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.WEBMVC_INSTRUMENTATION_NAME;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.*;
import static io.honeycomb.beeline.tracing.propagation.HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;

public class BeelineServletFilter implements Filter, BeelineInstrumentation {
    private static final String ASYNC_TIMEOUT_ERROR = "AsyncTimeout";

    private static final String X_REQUESTED_WITH_HEADER = "x-requested-with";
    private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    private static final String X_FORWARDED_PROTO_HEADER = "x-forwarded-proto";

    private final Tracer tracer;
    private final SpanBuilderFactory factory;
    private final BeelineProperties beelineProperties;
    private final List<String> includePaths;
    private final List<String> excludePaths;
    private final AntPathMatcher pathMatcher;

    public BeelineServletFilter(final BeelineProperties beelineProperties,
                                final Tracer tracer,
                                final SpanBuilderFactory factory,
                                final List<String> includePaths,
                                final List<String> excludePaths) {
        Assert.notNull(beelineProperties, "Validation failed: beelineProperties must not be null");
        Assert.notNull(factory, "Validation failed: factory must not be null");
        Assert.notNull(tracer, "Validation failed: tracer must not be null");

        this.beelineProperties = beelineProperties;
        this.tracer = tracer;
        this.factory = factory;
        this.includePaths = new ArrayList<>(includePaths);
        this.excludePaths = new ArrayList<>(excludePaths);
        this.pathMatcher = new AntPathMatcher();

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
        final Span currentFilterSpan = tracer.getActiveSpan();
        Exception exception = null;
        try {
            chain.doFilter(httpServletRequest, httpServletResponse);
        } catch (final Exception e) {
            exception = e;
            throw e;
        } finally {
            final Span detachedSpan = tracer.popSpan(currentFilterSpan);
            if (httpServletRequest.isAsyncStarted()) {
                detachedSpan.addField(SPRING_ASYNC_DISPATCH_FIELD, true);
                final AsyncListener listener = new TraceListener(detachedSpan);
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
        final Span childSpan = tracer.startChildSpan(spanName);
        childSpan
            .addField(SPRING_DISPATCHER_TYPE_FIELD, getDispatcherTypeName(request))
            .addField(TYPE_FIELD, InstrumentationConstants.OPERATION_TYPE_HTTP_SERVER);
        addHttpFields(childSpan, request);
    }

    protected void handleResponse(final HttpServletResponse httpServletResponse,
                                  final Throwable throwable,
                                  final Span currentSpan) {
        finishSpan(httpServletResponse, throwable, currentSpan);
    }

    protected void initializeRootSpan(final ServletServerHttpRequest request) {
        final String honeycombHeaderValue = request.getHeaders().getFirst(HONEYCOMB_TRACE_HEADER);
        final PropagationContext decoded = Propagation.honeycombHeaderV1().decode(honeycombHeaderValue);

        final String spanName = extractRootSpanName(request);

        final Span rootSpan = tracer.startTrace(
            factory
                .createBuilder()
                .setSpanName(spanName)
                .setServiceName(beelineProperties.getServiceName())
                .setParentContext(decoded)
                .addField(SPRING_DISPATCHER_TYPE_FIELD, getDispatcherTypeName(request))
                .addField(TYPE_FIELD, InstrumentationConstants.OPERATION_TYPE_HTTP_SERVER)
                .build()
        );
        addHttpFields(rootSpan, request);
    }

    protected void addHttpFields(final Span rootSpan, final ServletServerHttpRequest request) {

        final UriComponents uri = UriComponentsBuilder.fromUri(request.getURI()).build();
        final HttpHeaders headers = request.getHeaders();

        BeelineUtils.tryAddHeader(headers, rootSpan, HttpHeaders.CONTENT_TYPE, REQUEST_CONTENT_TYPE_FIELD);
        BeelineUtils.tryAddHeader(headers, rootSpan, HttpHeaders.ACCEPT, REQUEST_ACCEPT_FIELD);
        BeelineUtils.tryAddHeader(headers, rootSpan, HttpHeaders.USER_AGENT, USER_AGENT_FIELD);
        BeelineUtils.tryAddHeader(headers, rootSpan, X_FORWARDED_FOR_HEADER, FORWARD_FOR_HEADER_FIELD);
        BeelineUtils.tryAddHeader(headers, rootSpan, X_FORWARDED_PROTO_HEADER, FORWARD_PROTO_HEADER_FIELD);

        final MultiValueMap<String, String> queryParameters = uri.getQueryParams();
        if (!queryParameters.isEmpty()) {
            rootSpan.addField(REQUEST_QUERY_PARAMS_FIELD, queryParameters);
        }
        final long contentLength = request.getServletRequest().getContentLengthLong();
        if (contentLength != -1L) {
            rootSpan.addField(REQUEST_CONTENT_LENGTH_FIELD, contentLength);
        }

        BeelineUtils.tryAddField(rootSpan, REQUEST_HOST_FIELD, uri.getHost());
        BeelineUtils.tryAddField(rootSpan, REQUEST_PATH_FIELD, uri.getPath());
        BeelineUtils.tryAddField(rootSpan, REQUEST_SCHEME_FIELD, uri.getScheme());

        rootSpan
            .addField(REQUEST_METHOD_FIELD, request.getMethodValue())
            .addField(REQUEST_HTTP_VERSION_FIELD, request.getServletRequest().getProtocol())
            .addField(REQUEST_SECURE_FIELD, request.getServletRequest().isSecure())
            .addField(REQUEST_REMOTE_ADDRESS_FIELD, request.getServletRequest().getRemoteAddr())
            .addField(REQUEST_AJAX_FIELD, isAjax(headers));
    }

    protected String extractRootSpanName(final ServletServerHttpRequest servletRequest) {
        return InstrumentationConstants.FILTER_SPAN_NAME_PREFIX +
               servletRequest.getServletRequest().getMethod().toLowerCase(Locale.ENGLISH);
    }

    private String getDispatcherTypeName(final ServletServerHttpRequest request) {
        return request.getServletRequest().getDispatcherType().name();
    }

    private boolean isAjax(final HttpHeaders headers) {
        return "XMLHttpRequest".equalsIgnoreCase(headers.getFirst(X_REQUESTED_WITH_HEADER));
    }

    private static void finishSpan(final HttpServletResponse response,
                                   final Throwable throwable,
                                   final Span currentSpan) {
        if (currentSpan.isNoop()) {
            return;
        }

        if (throwable != null) {
            currentSpan.addField(REQUEST_ERROR_FIELD, throwable.getClass().getSimpleName());
            BeelineUtils.tryAddField(currentSpan, REQUEST_ERROR_DETAIL_FIELD, throwable.getMessage());
        }
        final String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
        BeelineUtils.tryAddField(currentSpan, RESPONSE_CONTENT_TYPE_FIELD, contentType);
        currentSpan.addField(STATUS_CODE_FIELD, response.getStatus());
        currentSpan.close();
    }

    public static class TraceListener implements AsyncListener {
        private final Span detachedSpan;
        private volatile boolean completed;

        public TraceListener(final Span detachedSpan) {
            this.detachedSpan = detachedSpan;
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
            finishSpan(suppliedResponse, event.getThrowable(), detachedSpan);
            completed = true;
        }

    }
}
