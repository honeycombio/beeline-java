package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import io.honeycomb.libhoney.transport.batch.impl.SystemClockProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SendingSpanTest {

    private Map<String, Object> traceFields = new HashMap<>();
    private Map<String, Object> fields = new HashMap<>();
    private ClockProvider clock = SystemClockProvider.getInstance();

    private Transport mockTransport;
    private HoneyClient client;
    private SpanPostProcessor processor;

    @Before
    public void setUp() throws Exception {
        mockTransport();
    }

    @Test
    public void WHEN_constructingASpan_THEN_valuesShouldEqualParameters() {
        final PropagationContext context = new PropagationContext("abc", "123", "myDataset", traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        assertThat(span.getSpanName()).isEqualTo("span1");
        assertThat(span.getServiceName()).isEqualTo("service1");
        assertThat(span.getParentSpanId()).isEqualTo("123");
        assertThat(span.getTraceId()).isEqualTo("abc");
        assertThat(span.getSpanId()).isEqualTo("$$$");
        assertThat(span.getDataset()).isEqualTo("myDataset");
        assertThat(span.getProcessor()).isEqualTo(processor);
    }

    @Test
    public void WHEN_constructingASpan_THEN_traceContextShouldContainTraceAndSpanIDs() {
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        assertThat(span.getTraceContext()).isEqualTo(new PropagationContext("abc", span.getSpanId(), null, Collections.emptyMap()));
    }

    @Test
    public void WHEN_constructingASpanWithFields_THEN_traceContextShouldContainTraceAndSpanIDsAndFields() {
        traceFields.put("key", "value");
        final PropagationContext context = new PropagationContext("abc", "123", "myDataset", traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        assertThat(span.getTraceContext()).isEqualTo(new PropagationContext("abc", span.getSpanId(), "myDataset", Collections.singletonMap("key", "value")));
    }

    @Test
    public void WHEN_constructingASpanWithFieldsAndAddingMoreFields_THEN_traceContextShouldContainAllFields() {
        traceFields.put("key", "value");
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.addTraceField("anotherKey", "anotherValue");

        assertThat(span.getTraceContext().getTraceFields()).containsOnly(entry("key", "value"), entry("anotherKey", "anotherValue"));
    }

    @Test
    public void WHEN_constructingASpanWithFieldsAndAddingMoreFieldsWithAMap_THEN_traceContextShouldContainAllFields() {
        traceFields.put("key", "value");
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.addTraceFields(Collections.singletonMap("anotherKey", "anotherValue"));

        assertThat(span.getTraceContext().getTraceFields()).containsOnly(entry("key", "value"), entry("anotherKey", "anotherValue"));
    }

    @Test
    public void WHEN_constructingASpan_THEN_spanFieldsShouldBeEmpty() {
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        assertThat(span.getFields()).isEmpty();
        assertThat(span.getTraceFields()).isEmpty();
    }

    @Test
    public void WHEN_addingTraceFields_THEN_spanHasOnlyTraceFields() {
        traceFields.put("key", "value");
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.addTraceField("traceKey", "traceValue");
        span.addTraceFields(Collections.singletonMap("traceKey2", "traceValue2"));

        assertThat(span.getTraceFields()).containsOnly(entry("traceKey", "traceValue"), entry("traceKey2", "traceValue2"), entry("key", "value"));
        assertThat(span.getFields()).isEmpty();
    }

    @Test
    public void WHEN_constructingASpan_EXPECT_elapsedTimeMeasurementToStartWithInitialisation() {
        clock = mock(ClockProvider.class);
        when(clock.getMonotonicTime()).thenReturn(100_000_000L, 200_000_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        assertThat(span.elapsedTimeMs()).isEqualTo(100);
        verify(clock, times(2)).getMonotonicTime();
        verify(clock).getWallTime();
    }

    @Test
    public void GIVEN_anInitialisedSpan_WHEN_callingMarkStart_EXPECT_elapsedTimeToBeReset() {
        clock = mock(ClockProvider.class);
        when(clock.getMonotonicTime()).thenReturn(100_000_000L, 300_000_000L, 1_000_000_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.markStart();

        assertThat(span.elapsedTimeMs()).isEqualTo(700L);
        verify(clock, times(3)).getMonotonicTime();
        verify(clock, times(2)).getWallTime();
    }

    @Test
    public void GIVEN_anInitialisedSpan_WHEN_callingMarkStartWithValues_EXPECT_elapsedTimeToBeReset() {
        clock = mock(ClockProvider.class);
        when(clock.getMonotonicTime()).thenReturn(100_000_000L, 1_000_000_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.markStart(0L, 300_000_000L);

        assertThat(span.elapsedTimeMs()).isEqualTo(700L);
        verify(clock, times(2)).getMonotonicTime();
        verify(clock, times(1)).getWallTime();
    }

    @Test
    public void GIVEN_anInitialisedSpan_WHEN_setTimes_EXPECT_parametersToBeSet() {
        clock = mock(ClockProvider.class);
        when(clock.getMonotonicTime()).thenReturn(100_000_000L, 300_000_000L, 1_000_000_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.markStart(5L, 10L);

        assertThat(span.getTimestamp()).isEqualTo(5L);
        assertThat(span.getStartTime()).isEqualTo(10L);
    }

    @Test
    public void WHEN_constructingASpan_EXPECT_timestampToBeSetWithInitialisation() {
        clock = mock(ClockProvider.class);
        when(clock.getWallTime()).thenReturn(100_000L, 200_000L);

        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        assertThat(span.getTimestamp()).isEqualTo(100_000L);
        verify(clock).getMonotonicTime();
        verify(clock).getWallTime();
    }

    @Test
    public void GIVEN_anInitialisedSpan_WHEN_callingMarkStart_EXPECT_timestampToBeReset() {
        clock = mock(ClockProvider.class);
        when(clock.getWallTime()).thenReturn(100_000L, 200_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.markStart();

        assertThat(span.getTimestamp()).isEqualTo(200_000L);
        verify(clock, times(2)).getMonotonicTime();
        verify(clock, times(2)).getWallTime();
    }

    @Test
    public void WHEN_addingFields_EXPECT_onlyFieldsToContainEntriesButNotTraceFields() {
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.addField("spanKey", "spanValue");
        span.addFields(Collections.singletonMap("spanKey2", "spanValue2"));

        assertThat(span.getFields()).containsOnly(entry("spanKey", "spanValue"), entry("spanKey2", "spanValue2"));
        assertThat(span.getTraceFields()).isEmpty();
    }

    @Test
    public void WHEN_callingEndOnSpan_EXPECT_eventToBeSubmittedWithAddedFields() {
        final Transport mockTransport = mockTransport();
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.addTraceField("traceKey", "traceValue");
        span.addTraceFields(Collections.singletonMap("traceKey2", "traceValue2"));
        span.addField("spanKey", "spanValue");
        span.addFields(Collections.singletonMap("spanKey2", "spanValue2"));
        span.close();
        final ResolvedEvent event = captureSubmittedEvent(mockTransport);

        assertThat(event.getFields())
            .contains(
                entry("traceKey", "traceValue"),
                entry("traceKey2", "traceValue2"),
                entry("spanKey", "spanValue"),
                entry("spanKey2", "spanValue2")
            );
    }

    @Test
    public void WHEN_callingEndOnSpan_EXPECT_systemFieldsToBeAdded() {
        final Transport mockTransport = mockTransport();
        clock = mock(ClockProvider.class);
        when(clock.getMonotonicTime()).thenReturn(300_000_000L, 1_000_000_000L);
        when(clock.getWallTime()).thenReturn(1_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.close();
        final ResolvedEvent event = captureSubmittedEvent(mockTransport);

        assertThat(event.getTimestamp()).isEqualTo(1_000L);
        assertThat(event.getFields())
            .contains(
                entry("service_name", "service1"),
                entry("name", "span1"),
                entry("duration_ms", 700.0),
                entry("trace.span_id", "$$$"),
                entry("trace.parent_id", "123"),
                entry("trace.trace_id", "abc")
            );
    }

    @Test
    public void WHEN_callingCloseOnASpan_EXPECT_systemFieldsToBeAdded() { // ie should behave the same as end()
        final Transport mockTransport = mockTransport();
        clock = mock(ClockProvider.class);
        when(clock.getMonotonicTime()).thenReturn(300_000_000L, 1_000_000_000L);
        when(clock.getWallTime()).thenReturn(1_000L);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.close();
        final ResolvedEvent event = captureSubmittedEvent(mockTransport);

        assertThat(event.getTimestamp()).isEqualTo(1_000L);
        assertThat(event.getFields())
            .contains(
                entry("service_name", "service1"),
                entry("name", "span1"),
                entry("duration_ms", 700.0),
                entry("trace.span_id", "$$$"),
                entry("trace.parent_id", "123"),
                entry("trace.trace_id", "abc")
            );
    }

    @Test
    public void WHEN_addingFields_EXPECT_systemFieldsNotToBeOverwritten() {
        final Transport mockTransport = mockTransport();
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "ricotta", "$$$",  fields, context, processor, clock, 1);

        span.addTraceField("service_name", "mozzarella");
        span.addField("service_name", "gorgonzola");
        span.close();
        final ResolvedEvent event = captureSubmittedEvent(mockTransport);

        assertThat(event.getFields()).containsEntry("service_name", "ricotta");
    }

    @Test
    public void WHEN_sendingASpan_EXPECT_sampleRateToBeSetOnEvent() {
        final Transport mockTransport = mockTransport();
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "ricotta", "$$$",  fields, context, processor, clock, 2);

        span.close();
        final ResolvedEvent event = captureSubmittedEvent(mockTransport);

        assertThat(event.getSampleRate()).isEqualTo(2);
    }

    @Test
    public void WHEN_postProcessorHasSamplingHook_EXPECT_sampleRateToBeProductOfItsSamplingRateAndInitialSamplingRate() {
        this.processor = new SpanPostProcessor(client, input -> 3);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "ricotta", "$$$",  fields, context, processor, clock, 2);

        span.close();
        final ResolvedEvent event = captureSubmittedEvent(mockTransport);

        assertThat(event.getSampleRate()).isEqualTo(6);
    }

    @Test
    public void WHEN_samplingHookSampleRateIs0_EXPECT_SpanToNotBeSubmitted() {
        this.processor = new SpanPostProcessor(client, input -> 0);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "ricotta", "$$$",  fields, context, processor, clock, 2);

        span.close();

        assertThat(span.getInitialSampleRate()).isEqualTo(2);
        verifyNoMoreInteractions(mockTransport);
    }

    @Test
    public void WHEN_initialSamplingRateIs0_EXPECT_SpanToNotBeSubmitted() {
        this.processor = new SpanPostProcessor(client, input -> 3);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "ricotta", "$$$",  fields, context, processor, clock, 0);

        span.close();

        assertThat(span.getInitialSampleRate()).isEqualTo(0);
        verifyNoMoreInteractions(mockTransport);
    }

    @Test
    public void GIVEN_anInitialisedSpan_WHEN_callingSetDuation_EXPECT_elapsedTimeToUseDuration() {
        clock = mock(ClockProvider.class);
        final PropagationContext context = new PropagationContext("abc", "123", null, traceFields);
        final SendingSpan span = new SendingSpan("span1", "service1", "$$$",  fields, context, processor, clock, 1);

        span.setDuration(500);

        assertThat(span.elapsedTimeMs()).isEqualTo(500);
        verify(clock, times(1)).getMonotonicTime(); // once for initial setup
        verify(clock, times(1)).getWallTime(); // once for initial setup
    }

    private Transport mockTransport() {
        mockTransport = mock(Transport.class);
        client = new HoneyClient(LibHoney.options()
            .setDataset("testDataset")
            .setWriteKey("testWriteKey")
            .build(), mockTransport);
        processor = new SpanPostProcessor(client, Sampling.alwaysSampler());
        final ResponseObservable mockObservable = spy(new ResponseObservable());
        when(mockTransport.getResponseObservable()).thenReturn(mockObservable);
        allowSubmissionToTransport(mockTransport);
        return mockTransport;
    }

    private ResolvedEvent captureSubmittedEvent(final Transport mockTransport) {
        final ArgumentCaptor<ResolvedEvent> eventCaptor = ArgumentCaptor.forClass(ResolvedEvent.class);
        verify(mockTransport).submit(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private void allowSubmissionToTransport(final Transport mockTransport) {
        when(mockTransport.submit(any(ResolvedEvent.class))).thenReturn(true);
    }

}
