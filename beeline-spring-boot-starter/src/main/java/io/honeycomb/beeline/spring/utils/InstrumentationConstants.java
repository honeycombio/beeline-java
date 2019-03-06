package io.honeycomb.beeline.spring.utils;

import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;

/**
 * Class to keep the names of non-standard spring-specific span fields.
 * See also {@link TraceFieldConstants}.
 */
public final class InstrumentationConstants {

    // @formatter:off
    // ========= Instrumentation constants =========
    /** Value given to the type field in the webmvc instrumentation. */
    public static final String OPERATION_TYPE_HTTP_SERVER       = "http_server";
    /** Prefix added to span names generated in the webmvc filter. */
    public static final String FILTER_SPAN_NAME_PREFIX          = "http_";
    /** Name of the Spring MVC web server instrumentation. */
    public static final String WEBMVC_INSTRUMENTATION_NAME      = "spring_mvc";
    /** Name of the Rest Template (Spring's http client) instrumentation. */
    public static final String REST_TEMPLATE_INSTRUMENTATION_NAME = "spring_rest_template";
    /** Name of the annotation-driven (via Spring AOP) instrumentation. */
    public static final String AOP_INSTRUMENTATION_NAME           = "spring_aop";
    /** Name of the http client span created by the rest template instrumentation. */
    public static final String HTTP_CLIENT_SPAN_NAME            = "http_client_request";
    /** Value given to the type field in the rest template instrumentation. */
    public static final String HTTP_CLIENT_SPAN_TYPE            = "http_client";
    /** Value given to the type field in the annotation-drive instrumentation. */
    public static final String ANNOTATED_METHOD_TYPE            = "annotated_method";
    // @formatter:on

    private InstrumentationConstants() {
        // constants class
    }
}
