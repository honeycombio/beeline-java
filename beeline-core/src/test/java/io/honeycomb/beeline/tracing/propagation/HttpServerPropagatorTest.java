package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerPropagatorTest {
    @Mock
    private Span mockSpan;
    @Mock
    private HttpServerRequestAdapter mockHttpRequest;
    @Mock
    private HttpServerResponseAdapter mockHttpResponse;
    @Mock
    private HttpServerRequestSpanCustomizer mockSpanCustomizer;
    @Mock
    private PropagationCodec<Map<String, String>> mockPropagationCodec;
    @Mock
    private Beeline mockBeeline;
    @Mock
    private PropagationContext mockPropagationContext;
    @Mock
    private Function<HttpServerRequestAdapter, PropagationContext> mockParseTraceHook;

    private HttpServerPropagator httpServerPropagator;

    private static final String EXPECTED_SERVICE_NAME = "expectedServiceName";
    private static final String EXPECTED_SPAN_NAME = "expectedSpanName";
    private static final Function<HttpServerRequestAdapter, String> REQUEST_TO_SPAN_NAME = r -> EXPECTED_SPAN_NAME;

    @Before
    public void setup() {
        stubFluentCalls(mockSpan);
        httpServerPropagator = new HttpServerPropagator.Builder(mockBeeline, EXPECTED_SERVICE_NAME, REQUEST_TO_SPAN_NAME)
                                                    .setSpanCustomizer(mockSpanCustomizer)
                                                    .setPropagationCodec(mockPropagationCodec)
                                                    .build();
    }

    @Test
    public void whenStartPropagation_traceIsStarted_andHttpFieldsAreApplied() {
        final String expectedTraceHeader = "expectedTraceHeader";
        when(mockHttpRequest.getHeaders()).thenReturn(Collections.singletonMap(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, expectedTraceHeader));
        when(mockPropagationCodec.decode(Collections.singletonMap(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, expectedTraceHeader))).thenReturn(mockPropagationContext);
        when(mockBeeline.startTrace(EXPECTED_SPAN_NAME, mockPropagationContext, EXPECTED_SERVICE_NAME)).thenReturn(mockSpan);

        final Span span = httpServerPropagator.startPropagation(mockHttpRequest);
        verify(mockSpanCustomizer).customize(span, mockHttpRequest);
    }

    @Test
    public void whenStartPropagation_andNoTraceHeaderIsPresent_traceIsStarted_andHttpFieldsAreApplied() {
        when(mockPropagationCodec.decode(Collections.emptyMap())).thenReturn(mockPropagationContext);
        when(mockBeeline.startTrace(EXPECTED_SPAN_NAME, mockPropagationContext, EXPECTED_SERVICE_NAME)).thenReturn(mockSpan);

        final Span span = httpServerPropagator.startPropagation(mockHttpRequest);
        verify(mockSpanCustomizer).customize(span, mockHttpRequest);
    }

    @Test
    public void whenEndPropagation_andOnlyErrorIsPresent_thenAddErrorFields() {
        final String expectedErrorMessage = "expectedErrorMessage";
        final TestException error = new TestException(expectedErrorMessage);

        httpServerPropagator.endPropagation(null, error, mockSpan);

        verify(mockSpan, times(2)).addField(anyString(), any());
        verify(mockSpan).addField(REQUEST_ERROR_FIELD, "TestException");
        verify(mockSpan).addField(REQUEST_ERROR_DETAIL_FIELD, expectedErrorMessage);

        verify(mockSpan).close();
    }

    @Test
    public void whenEndPropagation_andOnlyErrorIsPresent_withoutErrorMessage_doNotAddErrorMessageField() {
        final TestException error = new TestException();

        httpServerPropagator.endPropagation(null, error, mockSpan);

        verify(mockSpan).addField(anyString(), any());
        verify(mockSpan).addField(REQUEST_ERROR_FIELD, "TestException");

        verify(mockSpan).close();
    }


    @Test
    public void whenEndPropagation_andResponseIsPresent_thenAddRequiredResponseFields() {
        final int expectedStatus = 200;
        when(mockHttpResponse.getStatus()).thenReturn(expectedStatus);

        httpServerPropagator.endPropagation(mockHttpResponse, null, mockSpan);

        verify(mockSpan).addField(anyString(), any());
        verify(mockSpan).addField(STATUS_CODE_FIELD, expectedStatus);

        verify(mockSpan).close();
    }

    @Test
    public void whenEndPropagation_andResponseIsPresent_withContentType_thenAddAdditionalResponseFields() {
        final int expectedStatus = 200;
        when(mockHttpResponse.getStatus()).thenReturn(expectedStatus);
        final String expectedContentType = "expectedContentType";
        when(mockHttpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(Optional.of(expectedContentType));

        httpServerPropagator.endPropagation(mockHttpResponse, null, mockSpan);

        verify(mockSpan, times(2)).addField(anyString(), any());
        verify(mockSpan).addField(STATUS_CODE_FIELD, expectedStatus);
        verify(mockSpan).addField(RESPONSE_CONTENT_TYPE_FIELD, expectedContentType);

        verify(mockSpan).close();
    }

    @Test
    public void GIVEN_parseTraceHookIsNotNull_EXPECT_hookToBeUsedInsteadOfPropagationCodec() {

        // when(mockTracer.startChildSpan(EXPECTED_SPAN_NAME)).thenReturn(mockSpan);
        when(mockBeeline.startTrace(EXPECTED_SPAN_NAME, PropagationContext.emptyContext(), EXPECTED_SERVICE_NAME)).thenReturn(mockSpan);
        when(mockHttpRequest.getFirstHeader(any(String.class))).thenReturn(Optional.empty());
        when(mockHttpRequest.getPath()).thenReturn(Optional.empty());
        when(mockHttpRequest.getContentLength()).thenReturn(0);
        when(mockHttpRequest.getMethod()).thenReturn("GET");

        when(mockParseTraceHook.apply(mockHttpRequest)).thenReturn(PropagationContext.emptyContext());

        final HttpServerPropagator propagator = new HttpServerPropagator.Builder(mockBeeline, EXPECTED_SERVICE_NAME, REQUEST_TO_SPAN_NAME)
            .setParseTraceHook(mockParseTraceHook)
            .build();
        final Span span = propagator.startPropagation(mockHttpRequest);
        assertThat(span).isSameAs(mockSpan);
        verify(mockParseTraceHook, times(1)).apply(mockHttpRequest);
        verify(mockPropagationCodec, times(0)).encode(any(PropagationContext.class));
    }

    private static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }

        public TestException() {
            //nothing
        }
    }
}
