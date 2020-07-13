package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.utils.AntPathMatcher;
import io.honeycomb.beeline.tracing.utils.PathMatcher;
import io.honeycomb.beeline.tracing.utils.StringUtils;
import io.honeycomb.libhoney.shaded.org.apache.http.NameValuePair;
import io.honeycomb.libhoney.shaded.org.apache.http.client.utils.URIBuilder;
import io.honeycomb.libhoney.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_ERROR_DETAIL_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_ERROR_FIELD;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * A Servlet {@link Filter} implementation for tracing requests and submitting the spans to Honeycomb.
 * <p>
 * Creates a root span for incoming requests with {@link DispatcherType} {@code REQUEST}.
 * Will also create a child span if the request is redispatched, i.e. via an {@code FORWARD}, {@code INCLUDE}
 * or {@code ERROR} {@link DispatcherType}.
 * <p>
 * If the request is dispatched using the {@code ASYNC} {@link DispatcherType}, then the span will have fields
 * added to it that indicate this.
 * <p>
 * Optionally, the filter can be parameterized with functions that generate custom span names for request and redispatch
 * spans. By default the {@link #DEFAULT_REQUEST_SPAN_NAMING_FUNCTION} and {@link #DEFAULT_REDISPATCH_SPAN_NAMING_FUNCTION}
 * are used.
 * <p>
 * Optionally, the filter can be parameterized with a whitelist and/or a blacklist of request path patterns.
 * These are used to determine whether a request is included or excluded from being a candidate for tracing.
 * The patterns must be Ant-style path patterns. If no include patterns are specified then, by default, all requests are
 * included. If no exclude patterns are specified then, by default, no requests are excluded.
 * <p>
 * Optionally, the default path matching algorithm can be overridden by supplying your own instance of a {@link PathMatcher}
 * in the constructor.
 * <p>
 * Use {@link Builder} to construct an instance of the filter.
 * <p>
 * Compatible with Servlet 3.1+.
 *
 * @see AntPathMatcher
 */
public class BeelineServletFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(BeelineServletFilter.class);

    private static final String FILTER_SPAN_NAME_PREFIX = "http_";
    static final String ASYNC_DISPATCH_FIELD = "servlet.request.async_dispatch";
    static final String DISPATCHER_TYPE_FIELD = "servlet.request.dispatcher_type";
    static final String ASYNC_TIMEOUT_ERROR = "AsyncTimeout";

    public static final
    Function<io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter, String>
        DEFAULT_REQUEST_SPAN_NAMING_FUNCTION = r ->
         FILTER_SPAN_NAME_PREFIX +
            r.getMethod().toLowerCase(Locale.ENGLISH);

    public static final Function<HttpServletRequest, String> DEFAULT_REDISPATCH_SPAN_NAMING_FUNCTION = r ->
                 FILTER_SPAN_NAME_PREFIX +
                     r.getDispatcherType().name().toLowerCase(Locale.ENGLISH);

    private final List<String> includePaths;
    private final List<String> excludePaths;
    private final PathMatcher pathMatcher;
    private final HttpServerPropagator httpServerPropagator;
    private final HttpServerRequestSpanCustomizer spanRequestFieldsCustomizer;
    private final Beeline beeline;
    private final Function<HttpServletRequest, String> requestToRedispatchSpanName;

    protected BeelineServletFilter(final String serviceName,
                                   final Beeline beeline,
                                   final List<String> includePaths,
                                   final List<String> excludePaths,
                                   final Function<HttpServletRequest, String> requestToRedispatchSpanName,
                                   final Function<io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter, String> requestToSpanName,
                                   final PathMatcher pathMatcher) {
        Assert.notNull(serviceName, "Validation failed: serviceName must not be null");
        Assert.notNull(beeline, "Validation failed: beeline must not be null");

        this.beeline = beeline;
        this.includePaths = includePaths;
        this.excludePaths = excludePaths;
        this.pathMatcher = pathMatcher;
        this.spanRequestFieldsCustomizer = new HttpServerRequestSpanCustomizer();
        this.requestToRedispatchSpanName = requestToRedispatchSpanName;
        this.httpServerPropagator = new HttpServerPropagator(beeline,
            serviceName,
            requestToSpanName);
    }

    @Override
    public void doFilter(final ServletRequest request,
                         final ServletResponse response,
                         final FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        final HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        switch (request.getDispatcherType()) {
            case REQUEST:
                if (pathMatches(httpServletRequest)) {
                    initializeRootSpan(httpServletRequest);
                }
                break;
            case FORWARD:
            case ERROR:
            case INCLUDE: {
                initializeRedispatchSpan(httpServletRequest);
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
                detachedSpan.addField(getAsyncDispatchSpanFieldName(), true);
                final AsyncListener listener = new TraceListener(detachedSpan, httpServerPropagator);
                httpServletRequest.getAsyncContext().addListener(listener, httpServletRequest, httpServletResponse);
            } else {
                handleResponse(httpServletResponse, exception, detachedSpan);
            }
        }
    }

    protected String getAsyncDispatchSpanFieldName() {
        return ASYNC_DISPATCH_FIELD;
    }

    private boolean pathMatches(final HttpServletRequest request) {
        final URI uri;
        try {
            uri = new URI(request.getRequestURL().toString());
        } catch (final URISyntaxException e) {
            LOG.debug(
                "Exception parsing URI for white/blacklist check, so not tracing request - URI: '{}', Exception reason: '{}'",
                request.getRequestURL(), e.getReason());
            return false;
        }
        final String maybePath = uri.getPath();
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
        }
        for (final String pattern : this.includePaths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void initializeRedispatchSpan(final HttpServletRequest request) {
        final String spanName = requestToRedispatchSpanName.apply(request);
        final Span childSpan = beeline.getTracer().startChildSpan(spanName);
        childSpan.addField(getDispatcherTypeSpanFieldName(), request.getDispatcherType().name());
        spanRequestFieldsCustomizer.customize(childSpan, new HttpServerRequestAdapter(request));
    }

    protected String getDispatcherTypeSpanFieldName() {
        return DISPATCHER_TYPE_FIELD;
    }

    private void handleResponse(final HttpServletResponse httpServletResponse,
                                  final Throwable throwable,
                                  final Span currentSpan) {
        finishSpan(httpServerPropagator, httpServletResponse, throwable, currentSpan);
    }

    private void initializeRootSpan(final HttpServletRequest request) {
        final Span rootSpan = httpServerPropagator.startPropagation(new HttpServerRequestAdapter(request));
        rootSpan.addField(getDispatcherTypeSpanFieldName(), request.getDispatcherType().name());
    }

    private static void finishSpan(final HttpServerPropagator httpServerPropagator,
                                   final HttpServletResponse response,
                                   final Throwable throwable,
                                   final Span currentSpan) {
        if (throwable != null) {
            httpServerPropagator.endPropagation(null, throwable, currentSpan);
        } else {
            httpServerPropagator.endPropagation(new HttpServerResponseAdapter(response), null, currentSpan);
        }
    }

    protected static class TraceListener implements AsyncListener {
        private final Span detachedSpan;
        private final HttpServerPropagator httpServerPropagator;
        private volatile boolean completed;

        protected TraceListener(final Span detachedSpan, final HttpServerPropagator httpServerPropagator) {
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
            //We do not finish the span using the standard HttpServerPropagator#endPropagation in this case, as
            //the HTTP status and content type on the response object will not reflect what is returned to the client.
            //We mark the span as an error and leave the other HTTP response fields unset.
            detachedSpan.close();
            completed = true;
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

    /**
     * Returns a URI containing both the request URL and any query parameters.
     * <p>
     * If creating a URI that includes both the request URL and query parameters causes an exception,
     * then attempts to create a URI without the query parameters.
     *
     * @param request the servlet request
     * @return a URI, or {@code Options.empty()} if all attempts failed
     */
    private static Optional<URI> getURI(final HttpServletRequest request) {
        URI uri = null;
        try {
            final StringBuffer url = request.getRequestURL();
            if (StringUtils.hasText(request.getQueryString())) {
                url.append('?').append(request.getQueryString());
            }
            uri = new URI(url.toString());
        } catch (final URISyntaxException urlAndQueryEx) {
            LOG.debug(
                "Unable to parse request URL and query string as URI. Will attempt without query string. - Input: '{}', Reason: '{}'",
                urlAndQueryEx.getInput(), urlAndQueryEx.getReason());
            try {
                uri = new URI(request.getRequestURL().toString());
            } catch (final URISyntaxException justUrlEx) {
                LOG.debug(
                    "Unable to parse request URL as URI. Will not record request URI fields in span - Input: '{}', Reason: '{}'",
                    justUrlEx.getInput(), justUrlEx.getReason());
            }
        }
        return Optional.ofNullable(uri);
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        // no init needed
    }

    @Override
    public void destroy() {
        // no destroy needed
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Helper class to construct a {@link BeelineServletFilter}
     */
    public static class Builder {
        private String serviceName;
        private Beeline beeline;
        private List<String> includePaths = Collections.emptyList();
        private List<String> excludePaths = Collections.emptyList();
        private PathMatcher pathMatcher = new AntPathMatcher();
        private Function<HttpServletRequest, String>
            requestToRedispatchSpanName = DEFAULT_REDISPATCH_SPAN_NAMING_FUNCTION;
        private Function<io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter,
            String> requestToSpanName = DEFAULT_REQUEST_SPAN_NAMING_FUNCTION;

        /**
         * Set the name of the service using the filter. Required.
         * @param serviceName the name of the service using the filter
         * @return this
         */
        public Builder setServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Set the Beeline. Required.
         * @param beeline the beeline
         * @return this
         */
        public Builder setBeeline(Beeline beeline) {
            this.beeline = beeline;
            return this;
        }

        /**
         * Set the Ant patterns for matching requests to include when tracing.
         * <p>
         * Optional. By default all requests are included.
         * @param includePaths the Ant patterns for matching requests to include
         * @return this
         */
        public Builder setIncludePaths(List<String> includePaths) {
            this.includePaths = includePaths;
            return this;
        }

        /**
         * Set the Ant patterns for matching requests to exclude when tracing.
         * <p>
         * Optional. By default no requests are excluded.
         * @param excludePaths the Ant patterns for matching requests to exclude
         * @return this
         */
        public Builder setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
            return this;
        }

        /**
         * Set the path matcher to use when matching on request paths.
         * <p>
         * Optional. Defaults to {@link AntPathMatcher}.
         * @param pathMatcher the path matcher
         * @return this
         */
        public Builder setPathMatcher(PathMatcher pathMatcher) {
            this.pathMatcher = pathMatcher;
            return this;
        }

        /**
         * Sets the function to use when creating redispatch span names.
         * <p>
         * Optional. By default uses {@link BeelineServletFilter#DEFAULT_REDISPATCH_SPAN_NAMING_FUNCTION}.
         * @param requestToRedispatchSpanName the function to use when creating redispatch span names
         * @return this
         */
        public Builder setRequestToRedispatchSpanName(Function<HttpServletRequest, String> requestToRedispatchSpanName) {
            this.requestToRedispatchSpanName = requestToRedispatchSpanName;
            return this;
        }

        /**
         * Sets the function to use when creating request span names.
         * <p>
         * Optional. By default uses {@link BeelineServletFilter#DEFAULT_REQUEST_SPAN_NAMING_FUNCTION}.
         * @param requestToSpanName the function to use when creating request span names
         * @return this
         */
        public Builder setRequestToSpanName(Function<io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter, String> requestToSpanName) {
            this.requestToSpanName = requestToSpanName;
            return this;
        }

        public BeelineServletFilter build() {
            return new BeelineServletFilter(serviceName, beeline, includePaths, excludePaths,
                requestToRedispatchSpanName, requestToSpanName, pathMatcher);
        }
    }

    protected static class HttpServerRequestAdapter
        implements io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter {

        private final HttpServletRequest request;
        private final URIBuilder uriBuilder;
        private final  Map<String, List<String>> queryParams;

        protected HttpServerRequestAdapter(final HttpServletRequest request) {
            this.request = request;
            this.uriBuilder = getURI(request).map(URIBuilder::new).orElse(null);
            if (this.uriBuilder != null) {
                this.queryParams = uriBuilder.getQueryParams().stream()
                    .collect(groupingBy(NameValuePair::getName,
                        mapping(NameValuePair::getValue, toList())));
            } else {
                this.queryParams = Collections.emptyMap();
            }
        }

        @Override
        public String getMethod() {
            return request.getMethod();
        }

        @Override
        public Optional<String> getPath() {
            return Optional.ofNullable(uriBuilder).map(URIBuilder::getPath);
        }

        @Override
        public Optional<String> getFirstHeader(final String name) {
            return Optional.ofNullable(request.getHeader(name));
        }

        @Override
        public Optional<String> getScheme() {
            return Optional.ofNullable(uriBuilder).map(URIBuilder::getScheme);
        }

        @Override
        public Optional<String> getHost() {
            return Optional.ofNullable(uriBuilder).map(URIBuilder::getHost);
        }

        @Override
        public String getHttpVersion() {
            return request.getProtocol();
        }

        @Override
        public boolean isSecure() {
            return request.isSecure();
        }

        @Override
        public String getRemoteAddress() {
            return request.getRemoteAddr();
        }

        @Override
        public Map<String, List<String>> getQueryParams() {
            return queryParams;
        }

        @Override
        public int getContentLength() {
            return request.getContentLength();
        }

        @Override
        public Map<String, String> getHeaders() {
            Map<String, String> headers = Map.of();
            Enumeration<String> headerNames = request.getHeaderNames();

            if (headerNames != null) {
                return Collections.list(request.getHeaderNames())
                    .stream()
                    .collect(Collectors.toMap(
                        Function.identity(),
                        h -> request.getHeader(h)
                    ));
            }

            return headers;
        }
    }

    protected static class HttpServerResponseAdapter implements io.honeycomb.beeline.tracing.propagation.HttpServerResponseAdapter {
        private final HttpServletResponse response;

        protected HttpServerResponseAdapter(final HttpServletResponse response) {
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
