package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Further test cases in {@link BeelineRestTemplateInterceptorIntegrationTest}.
 */
public class BeelineRestTemplateInterceptorTest {

    private BeelineRestTemplateInterceptor interceptor;

    private Tracer mockTracer;
    private Span mockSpan;
    private HttpRequest mockRequest;
    private ClientHttpResponse mockResponse;
    private ClientHttpRequestExecution mockExecution;
    private byte[] body = {};

    @Before
    public void setUp() throws Exception {
        mockSpan = mock(Span.class);
        mockTracer = mock(Tracer.class);
        mockRequest = mock(HttpRequest.class);
        mockResponse = mock(ClientHttpResponse.class);
        mockExecution = mock(ClientHttpRequestExecution.class);

        when(mockSpan.addField(anyString(), any())).thenReturn(mockSpan);
        when(mockTracer.startChildSpan(anyString())).thenReturn(mockSpan);
        when(mockRequest.getHeaders()).thenReturn(HttpHeaders.EMPTY);
        when(mockRequest.getURI()).thenReturn(URI.create("localhost:8080"));
        when(mockResponse.getHeaders()).thenReturn(HttpHeaders.EMPTY);
        when(mockExecution.execute(eq(mockRequest), any(byte[].class))).thenReturn(mockResponse);

        interceptor = new BeelineRestTemplateInterceptor(mockTracer);
    }


    private void verifyNoErrorAndClose() {
        verify(mockSpan, never()).addField(eq("client.request.error"), any());
        verify(mockSpan).close();
    }

    @Test
    public void GIVEN_aMockedClientRequest_WHEN_interceptingRequest_EXPECT_requestSpanFieldsToBeAdded() throws IOException {
        body = new byte[]{1, 2, 3};
        when(mockRequest.getMethodValue()).thenReturn("post");
        when(mockRequest.getURI()).thenReturn(URI.create("/test/path"));
        final HttpHeaders headers = new HttpHeaders();
        headers.add("content-type", "text/plain");
        when(mockRequest.getHeaders()).thenReturn(headers);

        interceptor.intercept(mockRequest, body, mockExecution);

        verify(mockTracer).startChildSpan("http_client_request");
        verify(mockSpan).addField("type", "http_client");
        verify(mockSpan).addField("client.request.path", "/test/path");
        verify(mockSpan).addField("client.request.method", "post");
        verify(mockSpan).addField("client.request.content_length", 3);
        verify(mockSpan).addField("client.request.content_type", "text/plain");
        verifyNoErrorAndClose();

    }

    @Test
    public void GIVEN_aMockedClientRequestWithoutContent_WHEN_interceptingRequest_EXPECT_noContentTypeFieldToBeAdded() throws IOException {

        interceptor.intercept(mockRequest, body, mockExecution);

        verify(mockTracer).startChildSpan("http_client_request");
        verify(mockSpan).addField("type", "http_client");
        verify(mockSpan, never()).addField(eq("client.request.content_type"), any());
        verifyNoErrorAndClose();
    }

    @Test
    public void GIVEN_aMockedClientRequestWithoutPath_WHEN_interceptingRequest_EXPECT_noPathFieldToBeAdded() throws IOException {

        interceptor.intercept(mockRequest, body, mockExecution);

        verify(mockTracer).startChildSpan("http_client_request");
        verify(mockSpan).addField("type", "http_client");
        verify(mockSpan, never()).addField(eq("client.request.path"), any());
        verifyNoErrorAndClose();
    }

    @Test
    public void GIVEN_aMockedClientRequest_WHEN_interceptingRequestAndReceivingResponse_EXPECT_responseSpanFieldsToBeAdded() throws IOException {
        when(mockResponse.getRawStatusCode()).thenReturn(401);
        final HttpHeaders headers = new HttpHeaders();
        headers.add("content-type", "application/xml");
        headers.add("content-length", "3");
        when(mockResponse.getHeaders()).thenReturn(headers);

        interceptor.intercept(mockRequest, body, mockExecution);

        verify(mockTracer).startChildSpan("http_client_request");
        verify(mockSpan).addField("client.response.status_code", 401);
        verify(mockSpan).addField("client.response.content_type", "application/xml");
        verify(mockSpan).addField("client.response.content_length", "3");
        verifyNoErrorAndClose();
    }


    @Test
    public void GIVEN_aMockedClientRequest_WHEN_interceptingRequestAndReceivingResponseWithoutContent_EXPECT_noResponseContentFieldsToBeAdded() throws IOException {
        when(mockResponse.getRawStatusCode()).thenReturn(401);

        interceptor.intercept(mockRequest, body, mockExecution);

        verify(mockTracer).startChildSpan("http_client_request");
        verify(mockSpan).addField("client.response.status_code", 401);
        verify(mockSpan, never()).addField(eq("client.response.content_type"), any());
        verify(mockSpan, never()).addField(eq("client.response.content_length"), any());
        verifyNoErrorAndClose();
    }

    @Test
    public void GIVEN_clientThatThrowsAnException_WHEN_interceptingRequestAndReceivingException_EXPECT_errorFieldsToBeAdded() throws IOException {
        doThrow(new RuntimeException("Oh no!")).when(mockExecution).execute(any(), any());

        assertThatThrownBy(() -> interceptor.intercept(mockRequest, body, mockExecution)).isInstanceOf(RuntimeException.class);

        verify(mockTracer).startChildSpan("http_client_request");
        verify(mockSpan).addField("type", "http_client");
        verify(mockSpan, never()).addField(eq("client.response.status_code"), any());

        verify(mockSpan).addField("client.request.error", "RuntimeException");
        verify(mockSpan).addField("client.request.error_detail", "Oh no!");
        verify(mockSpan).close();
    }

}
