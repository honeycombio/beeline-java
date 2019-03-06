package io.honeycomb.beeline.spring.utils;

import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;

/**
 * Class to keep the names of fields not (yet) defined in the Beeline specs or ones that are spring/java-specific.
 * See also {@link TraceFieldConstants} for defined ones.
 */
public final class MoreTraceFieldConstants {
    // @formatter:off
    // ========= AOP fields =========
    /** Name of the annotated method. */
    public static final String JOIN_POINT_FIELD              = "spring.method.name";
    /** Exception name if the annotated method threw an exception. */
    public static final String JOIN_POINT_ERROR_FIELD        = "spring.method.error";
    /** Exception message if the annotated method threw an exception. */
    public static final String JOIN_POINT_ERROR_DETAIL       = "spring.method.error_detail";
    /** Prefix given to any captured parameter of an annotated method. */
    public static final String JOIN_POINT_PARAM_FIELD_PREFIX = "spring.method.param.";
    /** The return value of an annotated method. */
    public static final String JOIN_POINT_RESULT_FIELD       = "spring.method.result";

    // ========= http client fields =========
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

    // ========= request fields =========
    /** The response content type - if available. */
    public static final String RESPONSE_CONTENT_TYPE_FIELD  = "response.header.content_type";
    /** The request content type - if available. */
    public static final String REQUEST_CONTENT_TYPE_FIELD   = "request.header.content_type";
    /** The request accept header - if available. */
    public static final String REQUEST_ACCEPT_FIELD         = "request.header.accept";
    /** Any query parameters on the request URL. */
    public static final String REQUEST_QUERY_PARAMS_FIELD   = "request.query";

    // ========= spring-specific fields =========
    /** Indicates the dispatcher type of the span. */
    public static final String SPRING_DISPATCHER_TYPE_FIELD = "spring.request.dispatcher_type";
    /** Indicates the servlet request processing initiated an async dispatch. */
    public static final String SPRING_ASYNC_DISPATCH_FIELD  = "spring.request.async_dispatch";
    /** The type of handler (its class name) chosen to handle the request. */
    public static final String SPRING_HANDLER_CLASS_FIELD   = "spring.request.handler_type";
    /** The Spring controller method chosen to handle the request. A concatenation of class and method name. */
    public static final String SPRING_HANDLER_METHOD_FIELD  = "spring.request.handler_method";
    /** The URI pattern that was matched for the given request. */
    public static final String SPRING_MATCHED_PATTERN_FIELD = "spring.request.matched_pattern";
    // @formatter:on

    private MoreTraceFieldConstants() {
        // constants class
    }
}
