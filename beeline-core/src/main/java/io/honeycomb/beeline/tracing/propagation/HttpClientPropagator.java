package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.libhoney.shaded.org.apache.http.HttpHeaders;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;

/**
 * Provides straightforward instrumentation of an an HTTP client call, while also adding a
 * standardized set of span fields.
 *
 * <p>Example instrumentation for a synchronous client call:
 * <pre>{@code
 * HttpClientRequestAdapter adaptedHttpRequest = new HttpClientRequestAdapter(actualHttpRequest);
 * HttpClientResponseAdapter adaptedHttpResponse = null;
 * Throwable error = null;
 * Span span = clientPropagator.startPropagation(adaptedHttpRequest);
 * try {
 *   actualHttpResponse = makeClientCall(actualHttpRequest);
 *   adaptedHttpResponse = new HttpClientResponseAdapter(actualHttpResponse);
 * } catch (Exception ex) {
 *   error = ex;
 *   throw ex;
 * } finally {
 *   clientPropagator.endPropagation(adaptedHttpResponse, error, span);
 * }
 * }</pre>
 *
 *<p>
 * The API is based on Brave's HTTP utilities under {@code brave.http}.
 */
public class HttpClientPropagator {
    public static final String HTTP_CLIENT_SPAN_TYPE = "http_client";

    private final Tracer tracer;
    private final PropagationCodec<Map<String, String>> propagationCodec;
    private final Function<HttpClientRequestAdapter, String> requestToSpanName;
    private final BiFunction<HttpClientRequestAdapter, PropagationContext, Optional<Map<String, String>>> tracePropagationHook;

    /**
     * Create an HttpClientPropagator for tracing HTTP client requests.
     * <p>
     * {@code requestToSpanName} allows you to dynamically name the HTTP client spans such that the name
     * reflects the operation, e.g. based on HTTP method or request path used.
     *
     * @param tracer the tracer
     * @param requestToSpanName a function from request to span name
     */
    public HttpClientPropagator(final Tracer tracer, final Function<HttpClientRequestAdapter, String> requestToSpanName) {
        this(tracer, Propagation.defaultHeader(), requestToSpanName, null);
    }

    // Used by builder
    protected HttpClientPropagator(final Tracer tracer,
                                final PropagationCodec<Map<String, String>> propagationCodec,
                                final Function<HttpClientRequestAdapter, String> requestToSpanName,
                                final BiFunction<HttpClientRequestAdapter, PropagationContext, Optional<Map<String, String>>> tracePropagationHook) {
        this.tracer = tracer;
        this.propagationCodec = propagationCodec;
        this.requestToSpanName = requestToSpanName;
        this.tracePropagationHook = tracePropagationHook;
    }

    /**
     * Creates a child span for this HTTP client request and adds the standardized fields to it.
     *
     * @param httpRequest the adapted HTTP client request
     * @return the child span
     */
    public Span startPropagation(final HttpClientRequestAdapter httpRequest) {
        final Span childSpan = tracer.startChildSpan(requestToSpanName.apply(httpRequest));
        addRequestFields(httpRequest, childSpan);
        propagateTrace(httpRequest, childSpan);
        return childSpan;
    }

    /**
     * Adds standard span fields based on data in the HTTP response or the {@code error}.
     * Closes the HTTP client call span.
     * <p>
     * The {@code httpResponse} and the {@code error} are both allowed to be {@code null}.
     *
     * @param httpResponse the adapted HTTP response, may be {@code null}
     * @param error an error that was thrown during the HTTP client call, may be {@code null}
     * @param span the span for the HTTP client call
     */
    public void endPropagation(final HttpClientResponseAdapter httpResponse, final Throwable error, final Span span) {
        try {
            if (error != null) {
                addErrorFields(error, span);
            } else if (httpResponse != null) {
                addResponseFields(span, httpResponse);
            }
        } finally {
            span.close();
        }
    }

    private void addRequestFields(final HttpClientRequestAdapter httpRequest, final Span childSpan) {
        httpRequest.getFirstHeader(HttpHeaders.CONTENT_TYPE).ifPresent(v -> childSpan.addField(CLIENT_REQUEST_CONTENT_TYPE_FIELD, v));
        httpRequest.getPath().ifPresent(p -> childSpan.addField(CLIENT_REQUEST_PATH_FIELD, p));
        if (httpRequest.getContentLength() > 0) {
            childSpan.addField(CLIENT_REQUEST_CONTENT_LENGTH_FIELD, httpRequest.getContentLength());
        }
        childSpan
            .addField(TYPE_FIELD, HTTP_CLIENT_SPAN_TYPE)
            .addField(CLIENT_REQUEST_METHOD_FIELD, httpRequest.getMethod());
    }

    private void propagateTrace(final HttpClientRequestAdapter httpRequest, final Span childSpan) {
        if (tracePropagationHook == null) {
            propagationCodec.encode(childSpan.getTraceContext())
                .ifPresent(headers ->
                    headers.forEach((k,v) -> httpRequest.addHeader(k, v))
                );
        } else {
            tracePropagationHook.apply(httpRequest, childSpan.getTraceContext())
                .ifPresent(headers ->
                    headers.forEach((k,v) -> httpRequest.addHeader(k, v))
                );
        }
    }

    private void addResponseFields(final Span childSpan, final HttpClientResponseAdapter httpResponse) {
        childSpan.addField(CLIENT_RESPONSE_STATUS_CODE_FIELD, httpResponse.getStatus());
        httpResponse.getFirstHeader(HttpHeaders.CONTENT_LENGTH).ifPresent(v -> childSpan.addField(CLIENT_RESPONSE_CONTENT_LENGTH, v));
        httpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE).ifPresent(v -> childSpan.addField(CLIENT_RESPONSE_CONTENT_TYPE_FIELD, v));
    }

    private void addErrorFields(final Throwable ex, final Span childSpan) {
        childSpan.addField(CLIENT_REQUEST_ERROR_FIELD, ex.getClass().getSimpleName());
        if (ex.getMessage() != null) {
            childSpan.addField(CLIENT_REQUEST_ERROR_DETAIL_FIELD, ex.getMessage());
        }
    }

    /**
     * Builder for {@link HttpClientPropagator}.
     */
    public static class Builder {

        private Tracer tracer;
        private Function<HttpClientRequestAdapter, String> requestToSpanName;
        private PropagationCodec<Map<String, String>> propagationCodec = Propagation.defaultHeader();
        private BiFunction<HttpClientRequestAdapter, PropagationContext, Optional<Map<String, String>>> tracePropagationHook = null;

        /**
         * Creates a new instance of {@link HttpClientPropagator.Builder}.
         * @param tracer the tracer
         * @param requestToSpanName function to get a span name from a {@link HttpClientRequestAdapter}
         */
        public Builder(Tracer tracer, Function<HttpClientRequestAdapter, String> requestToSpanName) {
            this.tracer = tracer;
            this.requestToSpanName = requestToSpanName;
        }

        /**
         * Set the {@link PropagationCodec} to encode/decode trace context via HTTP headers.
         * @param propagationCodec
         * @return the {@link HttpClientPropagator.Builder} to be used for chaining
         */
        public Builder setPropagationCodec(PropagationCodec<Map<String, String>> propagationCodec) {
            this.propagationCodec = propagationCodec;
            return this;
        }

        /**
         * Set a custom function used to parse trace context on incoming HTTP requests.
         * @param tracePropagationHook
         * @return the {@link HttpClientPropagator.Builder} to be used for chaining
         */
        public Builder setTracePropagationHook(BiFunction<HttpClientRequestAdapter, PropagationContext, Optional<Map<String, String>>> tracePropagationHook) {
            this.tracePropagationHook = tracePropagationHook;
            return this;
        }

        /**
         * Builds a {@link HttpClientPropagator} using the parameters passed.
         * @return a new instance of {@link HttpClientPropagator}
         */
        public HttpClientPropagator build() {
            return new HttpClientPropagator(tracer, propagationCodec, requestToSpanName, tracePropagationHook);
        }
    }
}
