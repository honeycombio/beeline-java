package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.libhoney.shaded.org.apache.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.honeycomb.beeline.tracing.propagation.MockSpanUtils.stubFluentCalls;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientPropagatorTest {
    private static final String EXPECTED_TRACE_HEADER = "traceHeader1";
    private static final String EXPECTED_SPAN_NAME = "expectedSpanName";

    @Mock
    private Tracer mockTracer;
    @Mock
    private Span mockSpan;
    @Mock
    private HttpClientRequestAdapter mockHttpRequest;
    @Mock
    private HttpClientResponseAdapter mockHttpResponse;
    @Mock
    private PropagationCodec<Map<String, String>> mockPropagationCodec;

    private HttpClientPropagator httpClientPropagator;

    @Before
    public void setUp() {
        stubFluentCalls(mockSpan);
        httpClientPropagator = new HttpClientPropagator(mockTracer, mockPropagationCodec, r -> EXPECTED_SPAN_NAME, null);
    }

    @Test
    public void whenStartPropagation_traceHeaderIsAdded_onlyRequiredSpanFieldsAreAdded() {
        when(mockTracer.startChildSpan(EXPECTED_SPAN_NAME)).thenReturn(mockSpan);
        when(mockPropagationCodec.encode(any())).thenReturn(Optional.of(Collections.singletonMap(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, EXPECTED_TRACE_HEADER)));
        final String expectedHttpMethod = "httpMethod1";
        when(mockHttpRequest.getMethod()).thenReturn(expectedHttpMethod);

        final Span span = httpClientPropagator.startPropagation(mockHttpRequest);

        verify(span, times(2)).addField(anyString(), any());
        verify(span).addField(TYPE_FIELD, HttpClientPropagator.HTTP_CLIENT_SPAN_TYPE);
        verify(span).addField(CLIENT_REQUEST_METHOD_FIELD, expectedHttpMethod);
        verify(mockHttpRequest).addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, EXPECTED_TRACE_HEADER);
    }

    @Test
    public void whenStartPropagation_withAdditionalData_optionalSpanFieldsAreAdded_traceHeaderIsAdded() {
        when(mockTracer.startChildSpan(EXPECTED_SPAN_NAME)).thenReturn(mockSpan);
        when(mockPropagationCodec.encode(any())).thenReturn(Optional.of(Collections.singletonMap(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, EXPECTED_TRACE_HEADER)));
        final String expectedHttpMethod = "httpMethod1";
        when(mockHttpRequest.getMethod()).thenReturn(expectedHttpMethod);
        final String expectedPath = "expectedPath";
        when(mockHttpRequest.getPath()).thenReturn(Optional.of(expectedPath));
        final String expectedContentType = "expectedContentType";
        when(mockHttpRequest.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(Optional.of(expectedContentType));
        final int expectedContentLength = 1;
        when(mockHttpRequest.getContentLength()).thenReturn(expectedContentLength);

        final Span span = httpClientPropagator.startPropagation(mockHttpRequest);

        verify(span, times(5)).addField(anyString(), any());
        verify(span).addField(TYPE_FIELD, HttpClientPropagator.HTTP_CLIENT_SPAN_TYPE);
        verify(span).addField(CLIENT_REQUEST_METHOD_FIELD, expectedHttpMethod);
        verify(span).addField(CLIENT_REQUEST_CONTENT_TYPE_FIELD, expectedContentType);
        verify(span).addField(CLIENT_REQUEST_CONTENT_LENGTH_FIELD, expectedContentLength);
        verify(span).addField(CLIENT_REQUEST_PATH_FIELD, expectedPath);
        verify(mockHttpRequest).addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, EXPECTED_TRACE_HEADER);
    }

    @Test
    public void whenStartPropagation_andTraceHeaderIsGeneratedFromContext_doNotAddTraceHeader() {
        when(mockTracer.startChildSpan(EXPECTED_SPAN_NAME)).thenReturn(mockSpan);
        when(mockPropagationCodec.encode(any())).thenReturn(Optional.empty());
        final String expectedHttpMethod = "httpMethod1";
        when(mockHttpRequest.getMethod()).thenReturn(expectedHttpMethod);

        final Span span = httpClientPropagator.startPropagation(mockHttpRequest);

        verify(span, times(2)).addField(anyString(), any());
        verify(span).addField(TYPE_FIELD, HttpClientPropagator.HTTP_CLIENT_SPAN_TYPE);
        verify(span).addField(CLIENT_REQUEST_METHOD_FIELD, expectedHttpMethod);
        verify(mockHttpRequest, never()).addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, EXPECTED_TRACE_HEADER);
    }

    @Test
    public void whenEndPropagation_withNonNullResponse_thenOnlyAddRequiredResponseFields_andCloseSpan() {
        final int expectedHttpStatus = 200;
        when(mockHttpResponse.getStatus()).thenReturn(expectedHttpStatus);

        httpClientPropagator.endPropagation(mockHttpResponse, null, mockSpan);

        verify(mockSpan, times(1)).addField(anyString(), any());
        verify(mockSpan).addField(CLIENT_RESPONSE_STATUS_CODE_FIELD, expectedHttpStatus);

        verify(mockSpan).close();
    }

    @Test
    public void whenEndPropagation_withNonNullError_thenOnlyAddErrorFields_andCloseSpan() {
        final String expectedMessage = "expectedMessage";
        final TestException throwable = new TestException(expectedMessage);

        httpClientPropagator.endPropagation(null, throwable, mockSpan);

        verify(mockSpan, times(2)).addField(anyString(), any());
        verify(mockSpan).addField(CLIENT_REQUEST_ERROR_FIELD,"TestException");
        verify(mockSpan).addField(CLIENT_REQUEST_ERROR_DETAIL_FIELD, expectedMessage);
        verify(mockSpan).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_propagateIsNotNull_EXPECT_hookToBeUsedInsteadOfPropagationCodec() {

        when(mockTracer.startChildSpan(EXPECTED_SPAN_NAME)).thenReturn(mockSpan);
        when(mockHttpRequest.getFirstHeader(any(String.class))).thenReturn(Optional.empty());
        when(mockHttpRequest.getPath()).thenReturn(Optional.empty());
        when(mockHttpRequest.getContentLength()).thenReturn(0);
        when(mockHttpRequest.getMethod()).thenReturn("GET");

        final Function<HttpClientRequestAdapter, Optional<Map<String, String>>> mockEncodeFunc = (Function<HttpClientRequestAdapter, Optional<Map<String, String>>>) mock(Function.class);
        when(mockEncodeFunc.apply(mockHttpRequest)).thenReturn(Optional.empty());

        final HttpClientPropagator propagator = new HttpClientPropagator(mockTracer, mockPropagationCodec, r -> EXPECTED_SPAN_NAME, mockEncodeFunc);
        propagator.startPropagation(mockHttpRequest);
        verify(mockEncodeFunc, times(1)).apply(mockHttpRequest);
        verify(mockPropagationCodec, times(0)).encode(any(PropagationContext.class));
    }

    private class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}
