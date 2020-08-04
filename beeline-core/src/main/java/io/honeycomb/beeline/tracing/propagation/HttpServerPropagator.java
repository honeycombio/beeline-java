package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.libhoney.shaded.org.apache.http.HttpHeaders;

import java.util.Map;
import java.util.function.Function;

import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;

/**
 * Provides straightforward instrumentation of an an HTTP server, while also adding a
 * standardized set of span fields.
 *
 * <p>Example instrumentation for synchronous server code:
 * <pre>{@code
 * HttpServerRequestAdapter adaptedHttpRequest = new HttpServerRequestAdapter(actualHttpRequest);
 * HttpServerResponseAdapter adaptedHttpResponse = null;
 * Throwable error = null;
 * Span span = serverPropagator.startPropagation(adaptedHttpRequest);
 * try {
 *   actualHttpResponse = doServerWork(actualHttpRequest);
 *   adaptedHttpResponse = new HttpServerResponseAdapter(actualHttpResponse);
 * } catch (Exception ex) {
 *   error = ex;
 *   throw ex;
 * } finally {
 *   serverPropagator.endPropagation(adaptedHttpResponse, error, span);
 * }
 * }</pre>
 *
 * <p>
 * The API is based on Brave's HTTP utilities under {@code brave.http}.
 */
public class HttpServerPropagator {
    public static final String OPERATION_TYPE_HTTP_SERVER = "http_server";

    private final Function<HttpServerRequestAdapter, String> requestToSpanName;
    private final String serviceName;
    private final HttpServerRequestSpanCustomizer spanCustomizer;
    private final PropagationCodec<Map<String, String>> propagationCodec;
    private final Beeline beeline;

    /**
     * Create an HttpServerPropagator for tracing requests received by an HTTP server.
     * <p>
     * {@code requestToSpanName} allows you to dynamically name the HTTP server span such that the name
     * reflects the operation, e.g. based on HTTP method or request path used.
     *
     * @param beeline the beeline
     * @param serviceName the service name
     * @param requestToSpanName a function from an HTTP request to a span name
     */
    public HttpServerPropagator(final Beeline beeline,
                                final String serviceName,
                                final Function<HttpServerRequestAdapter, String> requestToSpanName) {
        this(serviceName, requestToSpanName, new HttpServerRequestSpanCustomizer(), Propagation.honeycombHeaderV1(), beeline);
    }

    /**
     * Create an HttpServerPropagator for tracing requests received by an HTTP server.
     * <p>
     * {@code requestToSpanName} allows you to dynamically name the HTTP server span such that the name
     * reflects the operation, e.g. based on HTTP method or request path used.

     * @param beeline the beeline
     * @param serviceName the service name
     * @param requestToSpanName a function from an HTTP request to a span name
     * @param propagationCodec a propagation codec used to parse trace data from incoming requests
     */
    public HttpServerPropagator(final Beeline beeline,
                                final String serviceName,
                                final Function<HttpServerRequestAdapter, String> requestToSpanName,
                                PropagationCodec<Map<String, String>> propagationCodec) {
        this(serviceName, requestToSpanName, new HttpServerRequestSpanCustomizer(), propagationCodec, beeline);
    }

    // Exposed for unit testing
    protected HttpServerPropagator(final String serviceName,
                                final Function<HttpServerRequestAdapter, String> requestToSpanName,
                                final HttpServerRequestSpanCustomizer spanCustomizer,
                                final PropagationCodec<Map<String, String>> propagationCodec,
                                final Beeline beeline) {
        this.requestToSpanName = requestToSpanName;
        this.serviceName = serviceName;
        this.spanCustomizer = spanCustomizer;
        this.propagationCodec = propagationCodec;
        this.beeline = beeline;
    }

    /**
     * Creates a root span for this HTTP server call and adds the standardized fields to it.
     *
     * @param httpRequest the adapted HTTP request
     * @return the span
     */
    public Span startPropagation(final HttpServerRequestAdapter httpRequest) {
        //PropagationCodec#decode is null-safe
        final PropagationContext decoded = propagationCodec.decode(httpRequest.getHeaders());

        final String spanName = requestToSpanName.apply(httpRequest);

        final Span rootSpan = beeline.startTrace(spanName, decoded, serviceName);

        spanCustomizer.customize(rootSpan, httpRequest);
        return rootSpan;
    }

    /**
     * Adds standard span fields based on data in the HTTP response or the {@code error}.
     * Closes the HTTP server span.
     * <p>
     * The {@code httpResponse} and the {@code error} are both allowed to be {@code null}.
     *
     * @param httpResponse the adapted HTTP response, may be {@code null}
     * @param error an error that was thrown when the HTTP server was processing the HTTP request, may be {@code null}
     * @param span the span for the HTTP server call
     */
    public void endPropagation(final HttpServerResponseAdapter httpResponse, final Throwable error, final Span span) {
        try {
            if (span.isNoop()) {
                return;
            }

            if (error != null) {
                span.addField(REQUEST_ERROR_FIELD, error.getClass().getSimpleName());
                if (error.getMessage() != null) {
                    span.addField(REQUEST_ERROR_DETAIL_FIELD, error.getMessage());
                }
            } else if (httpResponse != null) {
                httpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE).ifPresent(v -> span.addField(RESPONSE_CONTENT_TYPE_FIELD, v));
                span.addField(STATUS_CODE_FIELD, httpResponse.getStatus());
            }
        } finally {
            span.close();
        }
    }
}
