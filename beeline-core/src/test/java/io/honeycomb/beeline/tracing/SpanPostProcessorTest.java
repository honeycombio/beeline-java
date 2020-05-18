package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.Event;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.setLenientDateParsing;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class SpanPostProcessorTest {
    private TraceSampler<Span> mockSampler = mock(TraceSampler.class);
    private Span mockSpan = mock(Span.class);
    private HoneyClient mockClient = mock(HoneyClient.class);
    private Event mockEvent = mock(Event.class);

    private SpanPostProcessor spanPostProcessor = new SpanPostProcessor(mockClient, mockSampler);

    @Before
    public void setUp() {
        when(mockClient.createEvent()).thenReturn(mockEvent);
        when(mockEvent.addField(anyString(), any())).thenReturn(mockEvent);
        when(mockEvent.setTimestamp(anyLong())).thenReturn(mockEvent);
        when(mockSpan.addField(anyString(), anyString())).thenReturn(mockSpan);
    }

    @Test
    public void WHEN_runningSamplerHooke_EXPECT_spanToBePassedToTraceSampler() {
        when(mockSampler.sample(any(Span.class))).thenReturn(3);

        final int i = spanPostProcessor.runSamplerHook(mockSpan);

        assertThat(i).isEqualTo(3);
        verify(mockSampler).sample(mockSpan);
        verifyNoMoreInteractions(mockClient, mockSampler, mockSpan);
    }

    @Test
    public void WHEN_generatingEvent_THEN_eventShouldContainAllRequiredFields() {
        final PropagationContext context = new PropagationContext("traceId", "parentSpanId", "myDataset", Collections.singletonMap("traceKey", "traceValue"));
        mockSpan = new Span("spanName", "serviceName", "spanId", context, Collections.singletonMap("key", "value"), mock(ClockProvider.class));
        mockSpan.addField("anotherKey", "anotherValue");

        final Event event = spanPostProcessor.generateEvent(mockSpan);

        verify(event).addFields(mockSpan.getFields());
        verify(event).addFields(mockSpan.getTraceFields());
        verify(event).addField("trace.parent_id", "parentSpanId");
        verify(event).setDataset("myDataset");
        verify(event).setTimestamp(anyLong());
        verify(event).addField("service_name", "serviceName");
        verify(event).addField("name", "spanName");
        verify(event).addField("trace.span_id", "spanId");
        verify(event).addField("trace.trace_id", "traceId");
        verify(event).addField(eq("duration_ms"), anyDouble());
        verifyNoMoreInteractions(mockEvent, mockSampler);
    }

    @Test
    public void WHEN_generatingEvent_THEN_shouldGetEventFromClient() {
        spanPostProcessor.generateEvent(mockSpan);

        verify(mockClient).createEvent();
        verifyNoMoreInteractions(mockClient);
    }

    @Test
    public void WHEN_generatingEventWithoutParentSpanID_THEN_shouldNotBeAddingAParentSpanID() {
        final PropagationContext context = new PropagationContext("traceId", null, "myDataset", Collections.singletonMap("traceKey", "traceValue"));
        mockSpan = new Span("spanName", "serviceName", "spanId", context, Collections.singletonMap("key", "value"), mock(ClockProvider.class));
        mockSpan.addField("anotherKey", "anotherValue");

        final Event event = spanPostProcessor.generateEvent(mockSpan);

        verify(event, never()).addField(eq("trace.parent_id"), any());
    }

    @Test
    public void WHEN_generatingEventWithoutADataset_THEN_shouldNotBeSettingDatasetExplicitly() {
        final PropagationContext context = new PropagationContext("traceId", "parentSpanId", null, Collections.singletonMap("traceKey", "traceValue"));
        mockSpan = new Span("spanName", "serviceName", "spanId", context, Collections.singletonMap("key", "value"), mock(ClockProvider.class));
        mockSpan.addField("anotherKey", "anotherValue");

        final Event event = spanPostProcessor.generateEvent(mockSpan);

        verify(event, never()).setDataset(any());
    }

    @Test
    public void WHEN_closing_THEN_delegateToClient() {
        spanPostProcessor.close();

        verify(mockClient, times(1)).close();
        verifyNoMoreInteractions(mockClient, mockSampler, mockEvent, mockSpan);
    }
}
