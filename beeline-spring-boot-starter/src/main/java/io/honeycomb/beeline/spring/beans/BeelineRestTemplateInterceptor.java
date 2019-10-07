package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.HttpClientPropagator;
import io.honeycomb.libhoney.utils.Assert;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Optional;

import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.HTTP_CLIENT_SPAN_NAME;
import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.REST_TEMPLATE_INSTRUMENTATION_NAME;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.*;

public class BeelineRestTemplateInterceptor implements ClientHttpRequestInterceptor, BeelineInstrumentation {

    private final HttpClientPropagator httpClientPropagator;

    public BeelineRestTemplateInterceptor(final Tracer tracer) {
        Assert.notNull(tracer, "Validation failed: tracer must not be null");
        this.httpClientPropagator = new HttpClientPropagator(tracer, r -> HTTP_CLIENT_SPAN_NAME);
    }

    @Override
    public String getName() {
        return REST_TEMPLATE_INSTRUMENTATION_NAME;
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request,
                                        final byte[] body,
                                        final ClientHttpRequestExecution execution) throws IOException {
        final Span childSpan = httpClientPropagator.startPropagation(new HttpClientRequestAdapter(request, body));
        HttpClientResponseAdapter responseAdaptor = null;
        Throwable error = null;
        try {
            final ClientHttpResponse response = execution.execute(request, body);
            responseAdaptor = new HttpClientResponseAdapter(childSpan, response);
            return response;
        } catch (final Exception e) {
            error = e;
            throw e;
        } finally {
            httpClientPropagator.endPropagation(responseAdaptor, error, childSpan);
        }
    }

    public RestTemplateCustomizer customizer() {
        return (template) -> template.getInterceptors().add(0, this);
    }

    static final class HttpClientRequestAdapter implements io.honeycomb.beeline.tracing.propagation.HttpClientRequestAdapter {
        private final HttpRequest request;
        private final byte[] body;

        public HttpClientRequestAdapter(final HttpRequest request, final byte[] body) {
            this.request = request;
            this.body = body;
        }

        @Override
        public String getMethod() {
            return request.getMethodValue();
        }

        @Override
        public Optional<String> getPath() {
            return Optional.ofNullable(request.getURI().getPath());
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public void addHeader(String name, String value) {
            request.getHeaders().add(name, value);
        }

        @Override
        public Optional<String> getFirstHeader(String name) {
            return Optional.ofNullable(request.getHeaders().getFirst(name));
        }
    }

    static final class HttpClientResponseAdapter implements io.honeycomb.beeline.tracing.propagation.HttpClientResponseAdapter {
        private final Span span;
        private final ClientHttpResponse httpResponse;

        public HttpClientResponseAdapter(final Span span, final ClientHttpResponse httpResponse) {
            this.span = span;
            this.httpResponse = httpResponse;
        }

        @Override
        public int getStatus() {
            try {
                return httpResponse.getRawStatusCode();
            } catch (final IOException ex) {
                if (ex.getMessage() != null) {
                    span.addField(REST_TEMPLATE_RESPONSE_STATUS_ERROR_DETAIL_FIELD, ex.getMessage());
                }
                span.addField(REST_TEMPLATE_RESPONSE_STATUS_ERROR_FIELD, ex.getClass().getSimpleName());
                return -1;
            }
        }

        @Override
        public Optional<String> getFirstHeader(String name) {
            return Optional.ofNullable(httpResponse.getHeaders().getFirst(name));
        }
    }
}
