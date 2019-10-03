package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.libhoney.shaded.org.apache.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_AJAX_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_REMOTE_ADDRESS_FIELD;

/**
 * Customizes a span with information contained in an HTTP request received by the server.
 * <p>
 * This class is created for reuse so as not to violate DRY: it can be used in {@link HttpServerPropagator} and
 * in the Servlet Instrumentation in the Spring Beeline.However, it is not open for extension as the fields that the
 * instrumentation adds in this case are standardized.
 */
public final class HttpServerRequestSpanCustomizer {
    private static final String X_REQUESTED_WITH_HEADER = "x-requested-with";
    private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    private static final String X_FORWARDED_PROTO_HEADER = "x-forwarded-proto";

    /**
     * Customize a span by adding information from a HTTP request received by a server.
     * Adds all of the standard Honeycomb fields.
     * @param span the span
     * @param httpServerRequestAdapter the http server request
     */
    public void customize(final Span span, final HttpServerRequestAdapter httpServerRequestAdapter) {
        addHttpFields(span, httpServerRequestAdapter);
    }

    private void addHttpFields(final Span span, final HttpServerRequestAdapter request) {
        span.addField(TYPE_FIELD, HttpServerPropagator.OPERATION_TYPE_HTTP_SERVER);
        request.getFirstHeader(HttpHeaders.CONTENT_TYPE).ifPresent(v -> span.addField(REQUEST_CONTENT_TYPE_FIELD, v));
        request.getFirstHeader(HttpHeaders.ACCEPT).ifPresent(v -> span.addField(REQUEST_ACCEPT_FIELD, v));
        request.getFirstHeader(HttpHeaders.USER_AGENT).ifPresent(v -> span.addField(USER_AGENT_FIELD, v));
        request.getFirstHeader(X_FORWARDED_FOR_HEADER).ifPresent(v -> span.addField(FORWARD_FOR_HEADER_FIELD, v));
        request.getFirstHeader(X_FORWARDED_PROTO_HEADER).ifPresent(v -> span.addField(FORWARD_PROTO_HEADER_FIELD, v));

        final Map<String, List<String>> queryParameters = request.getQueryParams();
        if (!queryParameters.isEmpty()) {
            span.addField(REQUEST_QUERY_PARAMS_FIELD, queryParameters);
        }
        final int contentLength = request.getContentLength();
        if (contentLength > 0) {
            span.addField(REQUEST_CONTENT_LENGTH_FIELD, contentLength);
        }

        request.getHost().ifPresent(v -> span.addField(REQUEST_HOST_FIELD, v));
        request.getPath().ifPresent(v -> span.addField(REQUEST_PATH_FIELD, v));
        request.getScheme().ifPresent(v -> span.addField(REQUEST_SCHEME_FIELD, v));

        span
            .addField(REQUEST_METHOD_FIELD, request.getMethod())
            .addField(REQUEST_HTTP_VERSION_FIELD, request.getHttpVersion())
            .addField(REQUEST_SECURE_FIELD, request.isSecure())
            .addField(REQUEST_REMOTE_ADDRESS_FIELD, request.getRemoteAddress())
            .addField(REQUEST_AJAX_FIELD, isAjax(request));
    }

    private boolean isAjax(final HttpServerRequestAdapter httpRequest) {
        return httpRequest.getFirstHeader(X_REQUESTED_WITH_HEADER)
            .map("XMLHttpRequest"::equalsIgnoreCase)
            .orElse(false);
    }
}
