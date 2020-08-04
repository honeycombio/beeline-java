package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.libhoney.shaded.org.apache.http.HttpHeaders;

import java.util.Map;
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
        this(tracer, Propagation.honeycombHeaderV1(), requestToSpanName);
    }

    /**
     * Create an HttpClientPropagator for tracing HTTP client requests.
     * {@code requestToSpanName} allows you to dynamically name the HTTP client spans such that the name
     * reflects the operation, e.g. based on HTTP method or request path used.
     *
     * @param tracer the tracer
     * @param propagationCodec the propagation codec to use for parsing and propagating trace data
     * @param requestToSpanName a function from request to span name
     */
    public HttpClientPropagator(final Tracer tracer,
                                final PropagationCodec<Map<String, String>> propagationCodec,
                                final Function<HttpClientRequestAdapter, String> requestToSpanName) {
        this.tracer = tracer;
        this.propagationCodec = propagationCodec;
        this.requestToSpanName = requestToSpanName;
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
        propagationCodec.encode(childSpan.getTraceContext())
            .ifPresent(headers ->
                headers.forEach((k,v) -> httpRequest.addHeader(k, v))
            );
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
}
