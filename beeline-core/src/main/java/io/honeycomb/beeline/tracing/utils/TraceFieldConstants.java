package io.honeycomb.beeline.tracing.utils;

/**
 * Central class to keep the names of Honeycomb's common/standard span fields.
 */
public final class TraceFieldConstants {
    // @formatter:off
    // ========= user namespace =========
    public static final String USER_FIELD_NAMESPACE = "app.";
    // ========= trace namespace =========
    /** Unique ID (within trace) for a given event/span. */
    public static final String SPAN_ID_FIELD    = "trace.span_id";
    /** ID of a span's parent. No value if at the root. */
    public static final String PARENT_ID_FIELD  = "trace.parent_id";
    /** Global ID */
    public static final String TRACE_ID_FIELD   = "trace.trace_id";

    // ========= 'global' namespace =========
    /**
     * The type of a Span usually represents what aspect of an application the Span covers
     * (e.g. http_server, http_client).
     */
    public static final String TYPE_FIELD                   = "type";
    /** Name of the application being instrumented. */
    public static final String SERVICE_NAME_FIELD           = "service_name";
    /** Name of the operation the span covers. */
    public static final String SPAN_NAME_FIELD              = "name";
    /** Duration the operation took, in milliseconds (as a double). */
    public static final String DURATION_FIELD               = "duration_ms";

    // ========= meta namespace =========
    /** Name of the package under instrumentation. */
    public static final String PACKAGE_FIELD                = "meta.package";
    /** Version string of the package under instrumentation. */
    public static final String PACKAGE_VERSION_FIELD        = "meta.package_version";
    /** Version of the Beeline instrumentation. */
    public static final String BEELINE_VERSION_FIELD        = "meta.beeline_version";
    /** List of names showing what instrumentations are active. */
    public static final String INSTRUMENTATIONS_FIELD       = "meta.instrumentations";
    /** length of meta.instrumentations list. */
    public static final String INSTRUMENTATIONS_COUNT_FIELD = "meta.instrumentation_count";
    /** os.hostname() aka server name */
    public static final String LOCAL_HOSTNAME_FIELD         = "meta.local_hostname";
    /** Field key for specifying a span type as a Span Event */
    public static final String META_SPAN_TYPE_FIELD = "meta.span_type";
    /** Field value for specifying a span type as a Span Event */
    public static final String META_TYPE_SPAN_EVENT_VALUE = "span_event";
    /** Field value for specifying a span type as a Span Event */
    public static final String META_TYPE_LINK_EVENT_VALUE = "link";

    // ========= request namespace =========
    /** Value of the HTTP Host header. */
    public static final String REQUEST_HOST_FIELD           = "request.host";
    /** HTTP method value. */
    public static final String REQUEST_METHOD_FIELD         = "request.method";
    /** HTTP protocol version. */
    public static final String REQUEST_HTTP_VERSION_FIELD   = "request.http_version";
    /** The path component of the request URL. */
    public static final String REQUEST_PATH_FIELD           = "request.path";
    /** The scheme of the request URL (http/https). */
    public static final String REQUEST_SCHEME_FIELD         = "request.scheme";
    /** True if the connection is TLS/SSL. */
    public static final String REQUEST_SECURE_FIELD         = "request.secure";
    /** Content length of the request - if known. */
    public static final String REQUEST_CONTENT_LENGTH_FIELD = "request.content_length";
    /** The client IP address. */
    public static final String REQUEST_REMOTE_ADDRESS_FIELD = "request.remote_addr";
    /** True if the request is an AJAX request. */
    public static final String REQUEST_AJAX_FIELD           = "request.xhr";
    /** The value of the 'user agent' header. */
    public static final String USER_AGENT_FIELD             = "request.header.user_agent";
    /** The value of the 'forwarded for' header. */
    public static final String FORWARD_FOR_HEADER_FIELD     = "request.header.x_forwarded_for";
    /** The value of the 'forwarded proto' header */
    public static final String FORWARD_PROTO_HEADER_FIELD   = "request.header.x_forwarded_proto";
    /** Name of an error encountered that caused the request to fail (e.g. the name of an exception). */
    public static final String REQUEST_ERROR_FIELD          = "request.error";
    /** Detail about the error (e.g. the exception's message). */
    public static final String REQUEST_ERROR_DETAIL_FIELD   = "request.error_detail";
    /** The request content type - if available. */
    public static final String REQUEST_CONTENT_TYPE_FIELD   = "request.header.content_type";
    /** The request accept header - if available. */
    public static final String REQUEST_ACCEPT_FIELD         = "request.header.accept";
    /** Any query parameters on the request URL. */
    public static final String REQUEST_QUERY_PARAMS_FIELD   = "request.query";

    // ========= http client namespace =========
    /** */
    public static final String CLIENT_REQUEST_PATH_FIELD            = "client.request.path";
    /** */
    public static final String CLIENT_REQUEST_METHOD_FIELD          = "client.request.method";
    /** */
    public static final String CLIENT_REQUEST_CONTENT_LENGTH_FIELD  = "client.request.content_length";
    /** */
    public static final String CLIENT_REQUEST_CONTENT_TYPE_FIELD    = "client.request.content_type";
    /** */
    public static final String CLIENT_REQUEST_ERROR_FIELD           = "client.request.error";
    /** */
    public static final String CLIENT_REQUEST_ERROR_DETAIL_FIELD    = "client.request.error_detail";
    /** */
    public static final String CLIENT_RESPONSE_CONTENT_TYPE_FIELD   = "client.response.content_type";
    /** */
    public static final String CLIENT_RESPONSE_STATUS_CODE_FIELD    = "client.response.status_code";
    /** */
    public static final String CLIENT_RESPONSE_CONTENT_LENGTH       = "client.response.content_length";

    // ========= response namespace =========
    /** The response status code. */
    public static final String STATUS_CODE_FIELD            = "response.status_code";
    /** The response content type - if available. */
    public static final String RESPONSE_CONTENT_TYPE_FIELD  = "response.header.content_type";

    //// ========= indicators of problems =========
    /** A child span sent as result of its parent being closed before it was closed. */
    public static final String META_SENT_BY_PARENT_FIELD    = "meta.sent_by_parent";
    /** A span sent during cleanup, indicating a public API misuse (e.g. a trace was not properly closed). */
    public static final String META_DIRTY_CONTEXT_FIELD     = "meta.dirty_context";
    // @formatter:on

    private TraceFieldConstants() {
        // constants
    }
}
