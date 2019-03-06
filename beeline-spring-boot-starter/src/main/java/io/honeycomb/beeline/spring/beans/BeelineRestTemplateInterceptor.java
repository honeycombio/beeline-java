package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.spring.utils.BeelineUtils;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.HttpHeaderV1PropagationCodec;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.libhoney.utils.Assert;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.HTTP_CLIENT_SPAN_NAME;
import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.HTTP_CLIENT_SPAN_TYPE;
import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.REST_TEMPLATE_INSTRUMENTATION_NAME;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.*;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.TYPE_FIELD;

public class BeelineRestTemplateInterceptor implements ClientHttpRequestInterceptor, BeelineInstrumentation {

    private final Tracer tracer;

    public BeelineRestTemplateInterceptor(final Tracer tracer) {
        Assert.notNull(tracer, "Validation failed: tracer must not be null");

        this.tracer = tracer;
    }

    @Override
    public String getName() {
        return REST_TEMPLATE_INSTRUMENTATION_NAME;
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request,
                                        final byte[] body,
                                        final ClientHttpRequestExecution execution) throws IOException {
        final Span childSpan = tracer.startChildSpan(HTTP_CLIENT_SPAN_NAME);
        try {
            addRequestFields(request, body, childSpan);

            propagateTrace(request, childSpan);

            final ClientHttpResponse response = execution.execute(request, body);

            addResponseFields(childSpan, response);

            return response;
        } catch (final Exception e) {
            childSpan.addField(CLIENT_REQUEST_ERROR_FIELD, e.getClass().getSimpleName());
            BeelineUtils.tryAddField(childSpan, CLIENT_REQUEST_ERROR_DETAIL_FIELD, e.getMessage());
            throw e;
        } finally {
            childSpan.close();
        }
    }

    private void addRequestFields(final HttpRequest request, final byte[] body, final Span childSpan) {
        final HttpHeaders headers = request.getHeaders();
        BeelineUtils.tryAddHeader(headers, childSpan, HttpHeaders.CONTENT_TYPE, CLIENT_REQUEST_CONTENT_TYPE_FIELD);
        BeelineUtils.tryAddField(childSpan, CLIENT_REQUEST_PATH_FIELD, request.getURI().getPath());
        if (body.length > 0) {
            childSpan.addField(CLIENT_REQUEST_CONTENT_LENGTH_FIELD, body.length);
        }
        childSpan
            .addField(TYPE_FIELD, HTTP_CLIENT_SPAN_TYPE)
            .addField(CLIENT_REQUEST_METHOD_FIELD, request.getMethodValue());
    }

    private void addResponseFields(final Span childSpan, final ClientHttpResponse response) throws IOException {
        childSpan.addField(CLIENT_RESPONSE_STATUS_CODE_FIELD, response.getRawStatusCode());
        final HttpHeaders headers = response.getHeaders();
        BeelineUtils.tryAddHeader(headers, childSpan, HttpHeaders.CONTENT_LENGTH, CLIENT_RESPONSE_CONTENT_LENGTH);
        BeelineUtils.tryAddHeader(headers, childSpan, HttpHeaders.CONTENT_TYPE, CLIENT_RESPONSE_CONTENT_TYPE_FIELD);
    }

    public RestTemplateCustomizer customizer() {
        return (template) -> template.getInterceptors().add(0, this);
    }

    private void propagateTrace(final HttpRequest request, final Span childSpan) {
        Propagation.honeycombHeaderV1().encode(childSpan.getTraceContext())
            .ifPresent(headerValue ->
                request.getHeaders().add(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, headerValue)
            );
    }
}
