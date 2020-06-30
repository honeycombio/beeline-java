package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.ids.W3CTraceIdProvider;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import io.honeycomb.libhoney.transport.batch.impl.SystemClockProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableMap.of;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.META_SENT_BY_PARENT_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_ERROR_DETAIL_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_ERROR_FIELD;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.*;

public class TracerTest {

    private HoneyClient client;
    private Tracer tracer;
    private SpanBuilderFactory factory;

    private Transport mockTransport;
    private ArgumentCaptor<ResolvedEvent> eventCaptor = ArgumentCaptor.forClass(ResolvedEvent.class);

    private Set<String> problemFields = new HashSet<>(Arrays.asList(
        REQUEST_ERROR_FIELD,
        REQUEST_ERROR_DETAIL_FIELD,
        META_SENT_BY_PARENT_FIELD));

    @Before
    public void setUp() {
        mockTransport = mockTransport();
        client = new HoneyClient(LibHoney.options()
            .setDataset("testDataset")
            .setWriteKey("testWriteKey")
            .build(), mockTransport);
        final SpanPostProcessor spanPostProcessor = new SpanPostProcessor(client, Sampling.alwaysSampler());
        factory = new SpanBuilderFactory(spanPostProcessor, SystemClockProvider.getInstance(), W3CTraceIdProvider.getInstance(), Sampling.alwaysSampler());
        tracer = new Tracer(factory);

    }

    private Transport mockTransport() {
        final Transport mockTransport = mock(Transport.class);
        when(mockTransport.submit(any(ResolvedEvent.class))).thenReturn(true);

        final ResponseObservable mockObservable = mock(ResponseObservable.class);
        when(mockTransport.getResponseObservable()).thenReturn(mockObservable);

        return mockTransport;
    }

    @Test
    public void GIVEN_noActiveTrace_WHEN_endingTrace_EXPECT_NoEventToBeSent() {
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_aCallToStartTraceOnly_EXPECT_NoEventToBeSent() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTrace_EXPECT_AnEventToBeSent() {
        tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());

        tracer.endTrace();

        assertThatEventHasFields(of(
            "service_name", "serviceA",
            "name", "span1")
        );
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTheSpanDirectly_EXPECT_AnEventToBeSent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());

        span.close();

        assertThatEventHasFields(of(
            "service_name", "serviceA",
            "name", "span1")
        );
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTrace_EXPECT_ACompleteEventToBeSent() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());

        tracer.endTrace();

        assertThatSentEventHasRequiredFields(rootSpan);
    }

    private void assertThatSentEventHasRequiredFields(final Span rootSpan) {
        final ResolvedEvent capturedEvent = captureEvent();
        final Map<String, Object> eventFields = capturedEvent.getFields();
        assertThat(capturedEvent.getDataset()).isEqualTo("testDataset");
        assertThat(capturedEvent.getWriteKey()).isEqualTo("testWriteKey");
        assertThat(eventFields).containsAllEntriesOf(of(
            "service_name", "serviceA",
            "name", "span1",
            "trace.span_id", rootSpan.getSpanId(),
            "trace.trace_id", rootSpan.getTraceId()
        ));
        assertThat(eventFields).containsEntry("service_name", "serviceA");
        assertThat(eventFields).containsEntry("name", "span1");
        assertThat(eventFields).containsEntry("trace.span_id", rootSpan.getSpanId());
        assertThat(eventFields).containsEntry("trace.trace_id", rootSpan.getTraceId());
        assertThat(eventFields).containsKey("duration_ms");
        assertThat(eventFields).doesNotContainKeys("trace.parent_di", "meta.dirty_context", "meta.sent_by_parent");
    }

    @Test
    public void GIVEN_anActiveTraceWithAProvidedContext_WHEN_endingTrace_EXPECT_ACompleteEventToBeSent() {
        final PropagationContext context = new PropagationContext("traceId123", "parentIdABC", null, singletonMap("key1", "value1"));
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").setParentContext(context).build());

        tracer.endTrace();

        assertThatSentEventHasRequiredFieldsAndContextIds(rootSpan);
    }

    private void assertThatSentEventHasRequiredFieldsAndContextIds(final Span rootSpan) {
        final ResolvedEvent capturedEvent = captureEvent();
        final Map<String, Object> eventFields = capturedEvent.getFields();
        assertThat(capturedEvent.getDataset()).isEqualTo("testDataset");
        assertThat(capturedEvent.getWriteKey()).isEqualTo("testWriteKey");
        assertThat(eventFields).containsAllEntriesOf(of(
            "service_name", "serviceA",
            "name", "span1",
            "trace.span_id", rootSpan.getSpanId(),
            "trace.trace_id", "traceId123",
            "trace.parent_id", "parentIdABC"
        ));
        assertThat(eventFields).containsKey("duration_ms");
        assertThat(eventFields).doesNotContainKeys("meta.dirty_context", "meta.sent_by_parent");
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_addingFieldsToSpan_EXPECT_fieldsToBePresentOnSubmittedEvent() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());
        rootSpan.addField("span-info-1", "info-0123");
        rootSpan.addField("span-info-2", "info-9876");

        tracer.endTrace();

        assertThatEventHasFields(of(
            "span-info-1", "info-0123",
            "span-info-2", "info-9876"
        ));
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_addingFieldsAndTraceFieldsToSpan_EXPECT_fieldsToBePresentOnSubmittedEvent() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());
        rootSpan.addField("span-info-1", "info-0123");
        rootSpan.addTraceField("trace-field-1", "654");

        tracer.endTrace();

        assertThatEventHasFields(of(
            "span-info-1", "info-0123",
            "trace-field-1", "654"
        ));
    }

    @Test
    public void GIVEN_anActiveTraceWithVariousFieldsAdded_WHEN_endingChildSpan_EXPECT_fieldsToNotContainParentFieldsButDoContainTraceFields() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());
        rootSpan.addField("span-info-1", "info-0123");
        rootSpan.addTraceField("trace-field-1", "654");
        final Span childSpan = tracer.startChildSpan("childSpan");
        childSpan.addField("span-info-2", "info-9876");
        childSpan.addTraceField("trace-field-2", "789");

        childSpan.close();

        assertThatEventHasFields(of(
            "trace-field-1", "654",
            "span-info-2", "info-9876",
            "trace-field-2", "789"
        ));
        assertThatEventDoesntHaveField("span-info-1");
    }

    @Test
    public void GIVEN_anActiveTraceWithVariousFieldsAdded_WHEN_endingChildThenRoot_EXPECT_parentFieldsToNotContainChildFields() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span1").setServiceName("serviceA").build());
        rootSpan.addField("span-info-1", "info-0123");
        rootSpan.addTraceField("trace-field-1", "654");
        final Span childSpan = tracer.startChildSpan("childSpan");
        childSpan.addTraceField("trace-field-2", "789");
        childSpan.addField("span-info-2", "info-9876");

        childSpan.close();
        rootSpan.close();

        verify(mockTransport, times(2)).submit(eventCaptor.capture());
        final Map<String, Object> eventFields = eventCaptor.getValue().getFields();
        assertThat(eventFields).containsAllEntriesOf(of(
            "span-info-1", "info-0123",
            "trace-field-1", "654"
        ));
        assertThat(eventFields).doesNotContainKeys("span-info-2", "trace-field-2");
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTheSpanDirectlyThenEndingTheTrace_EXPECT_OnlyOneEventToBeSent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        span.close();
        tracer.endTrace();

        verify(mockTransport).submit(any(ResolvedEvent.class));
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTheTraceThenEndingTheSpan_EXPECT_OnlyOneEventToBeSent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        tracer.endTrace();
        span.close();

        verify(mockTransport).submit(any(ResolvedEvent.class));
    }

    @Test
    public void GIVEN_anActiveTrace_EXPECT_anActiveSpanToBePresent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());

        final Span activeSpan = tracer.getActiveSpan();

        assertThat(activeSpan.getSpanName()).isEqualTo("testSpan");
        assertThat(activeSpan.getServiceName()).isEqualTo("service");
    }

    @Test
    public void GIVEN_noActiveTrace_EXPECT_NoopSpanToBeActive() {
        assertThat(tracer.getActiveSpan()).isNotNull();
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTrace_EXPECT_noActiveSpanToBePresent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());

        tracer.endTrace();

        assertThat(tracer.getActiveSpan()).isNotNull();
        assertThatNoSpanIsActive();
    }

    @Test
    public void checkNoopInstanceIsAlwaysTheSame() {
        final Span noopSpan = tracer.getActiveSpan();
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        tracer.endTrace();

        final Span noopSpan2 = tracer.getActiveSpan();

        assertThat(noopSpan).isSameAs(noopSpan2);
    }

    @Test
    public void GIVEN_aNoopSpan_WHEN_startingAndEndingATrace_EXPECT_nothingToBeSent() {
        tracer.startTrace(Span.getNoopInstance());
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_aTracerThatNeverSamplesTraces_WHEN_startingAndEndingATraceViaTheRootSpan_EXPECT_nothingToBeSent() {
        final Span rootSpan = tracer.startTrace(Span.getNoopInstance());
        rootSpan.close();

        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingRootSpan_EXPECT_noActiveSpanToBePresent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());

        span.close();

        assertThat(tracer.getActiveSpan()).isNotNull();
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_callingEndTwiceOnASpan_EXPECT_OnlyOneEventToBeSent() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        span.close();
        span.close();

        verify(mockTransport).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_callingEndTraceTwice_EXPECT_EventsToBeSentOnce() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());
        tracer.startChildSpan("childSpan");

        tracer.endTrace();
        tracer.endTrace();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_emptyArgumentsToStartChildSpan_EXPECT_IAEToBeThrown() {
        assertThatThrownBy(() -> tracer.startChildSpan("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tracer.startChildSpan(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingChildSpan_EXPECT_OnlySubSpanToBeSentAndActiveSpanSetBackToParent() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("childSpan");

        childSpan.close();

        verify(mockTransport).submit(any(ResolvedEvent.class));
        assertThat(tracer.getActiveSpan()).isEqualTo(rootSpan);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTraceOnAChildSpan_EXPECT_childSpanAndRootSpanToBeCleanupUpAndSubmitted() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        tracer.startChildSpan("childSpan");

        tracer.endTrace();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTraceWithActiveGrandChildSpan_EXPECT_AllSpansToBeSubmitted() {
        final Span span = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        tracer.startChildSpan("childSpan");
        tracer.startChildSpan("grandChildSpan");

        tracer.endTrace();

        verify(mockTransport, times(3)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingTraceViaRootSpan_EXPECT_AllSpansToBeSubmitted() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        tracer.startChildSpan("childSpan");
        tracer.startChildSpan("grandChildSpan");

        rootSpan.close();

        verify(mockTransport, times(3)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingChildSpanWithActiveGrandChild_EXPECT_rootNotToBeSubmitted() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("childSpan");
        final Span grandChildSpan = tracer.startChildSpan("grandChildSpan");

        childSpan.close();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThat(tracer.getActiveSpan()).isEqualTo(rootSpan);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_endingChildSpanWithActiveGrandChild_EXPECT_grandChildToBeSubmittedWithSentByParentField() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("childSpan");
        final Span grandChildSpan = tracer.startChildSpan("grandChildSpan");

        childSpan.close();

        final List<ResolvedEvent> resolvedEvent = captureAllEvents();
        assertThat(resolvedEvent.get(0).getFields()).contains(
            entry("name", "grandChildSpan"),
            entry("meta.sent_by_parent", true)
        );
        assertThat(resolvedEvent.get(1).getFields()).contains(
            entry("name", "childSpan")
        ).doesNotContain(entry("meta.sent_by_parent", true));

        assertThat(tracer.getActiveSpan()).isEqualTo(rootSpan);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_startingANewTraceWithoutEndingThePrevious_EXPECT_previousSpansToBeSubmitted() {
        final Span noopSpan = tracer.getActiveSpan();
        tracer.startTrace(factory.createBuilder().setSpanName("testSpan").setServiceName("service").build());
        tracer.startChildSpan("childSpan");

        final Span newSpan = tracer.startTrace(factory.createBuilder().setSpanName("newTestSpan").setServiceName("service").build());

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThat(tracer.getActiveSpan()).isEqualTo(newSpan);
    }

    @Test
    public void checkThatStartingAChildSpanWhenNoTraceIsActiveReturnsTheNoopSpan() {
        final Span childSpan = tracer.startChildSpan("childSpan");
        childSpan.close();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThat(childSpan.isNoop()).isTrue();
    }

    @Test
    public void checkThatNoopSpanDoesNothing() {
        final Span noopSpan = tracer.getActiveSpan();
        noopSpan.addTraceField("key", "value");
        noopSpan.close();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_noActiveTrace_EXPECT_detachedSpanToBeNoop() {
        final Span noopSpan = tracer.getActiveSpan();
        final Span childSpan = tracer.startDetachedChildSpan("childSpan");
        childSpan.close();
        tracer.endTrace();

        assertThat(childSpan.isNoop()).isTrue();
        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_activeTraceAndADetachedSpan_WHEN_endingTrace_EXPECT_detachedSpanToNotBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("spanB").setServiceName("serviceA").build());
        final Span detachedSpan = tracer.startDetachedChildSpan("detachedSpan");
        tracer.endTrace();

        assertThatEventHasFields(of(
            "service_name", "serviceA",
            "name", "spanB")
        );
        assertThat(detachedSpan.getSpanName()).isEqualTo("detachedSpan");
    }

    @Test
    public void GIVEN_activeTraceAndADetachedSpan_WHEN_endingDetachedSpan_EXPECT_detachedSpanToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("spanB").setServiceName("serviceA").build());
        final Span detachedSpan = tracer.startDetachedChildSpan("detachedSpan");
        detachedSpan.close();

        assertThatEventHasFields(of(
            "service_name", "serviceA",
            "name", "detachedSpan")
        );
    }

    @Test
    public void WHEN_creatingADetachedSpan_EXPECT_ParentIdToBeCorrect() {
        final Span parentSpan = tracer.startTrace(factory.createBuilder().setSpanName("spanB").setServiceName("serviceA").build());

        final Span detachedSpan = tracer.startDetachedChildSpan("detachedSpan");

        assertThat(detachedSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(detachedSpan.getSpanName()).isEqualTo("detachedSpan");
    }

    ///////////////////////////////////////////////////////////////////////////
    // TRACED RUNNABLE TESTS
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aTracedSyncRunnable_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Runnable childSpanB = tracer.traceRunnable(
            "childSpanB",
            () -> assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB"));

        childSpanB.run();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncRunnable_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncRunnable_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncRunnable_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Runnable childSpanB = tracer.traceRunnable(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                throw new IllegalStateException("Oops!");
            });

        try {
            childSpanB.run();
        } catch (IllegalStateException e) {
        }
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncRunnable_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncRunnable_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncRunnable_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Runnable childSpanB = tracer.traceRunnable(
            "childSpanB",
            () -> assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB"));

        tracer.endTrace();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        childSpanB.run();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncRunnable_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncRunnable_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncRunnable_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<Void> childSpanB = CompletableFuture.runAsync(
            tracer.traceRunnable(
                "childSpanB",
                () -> assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB")));

        tracer.endTrace();
        childSpanB.join();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncRunnable_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncRunnable_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncRunnable_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<Void> childSpanB = CompletableFuture.runAsync(
            tracer.traceRunnable(
                "childSpanB",
                () -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    throw new IllegalStateException("Oops!");
                }));

        tracer.endTrace();
        assertThatThrownBy(childSpanB::join).hasCauseInstanceOf(IllegalStateException.class);

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncRunnable_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncRunnable_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncRunnable_AND_NoTraceIsActive_WHEN_runningItAndEndingTheTrace_EXPECT_nothingToHappen() {
        final Runnable childSpanB = tracer.traceRunnable(
            "childSpanB",
            () -> assertThat(tracer.getActiveSpan().isNoop()).isTrue());

        childSpanB.run();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncRunnable_AND_NoTraceIsActive_WHEN_waitingForItToComplete_EXPECT_nothingToHappen() {
        final CompletableFuture<Void> childSpanB = CompletableFuture.runAsync(
            tracer.traceRunnable(
                "childSpanB",
                () -> assertThat(tracer.getActiveSpan().isNoop()).isTrue()));

        childSpanB.join();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    ///////////////////////////////////////////////////////////////////////////
    // TRACED CALLABLE TESTS
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aTracedSyncCallable_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() throws Exception {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Callable<String> childSpanB = tracer.traceCallable(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                return "test";
            });

        childSpanB.call();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncCallable_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() throws Exception {
        GIVEN_aTracedSyncCallable_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncCallable_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway() throws Exception {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Callable<String> childSpanB = tracer.traceCallable(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                throw new IllegalStateException("Oops!");
            });

        try {
            childSpanB.call();
        } catch (IllegalStateException e) {
        }
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncCallable_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway_AND_DoesNotContainProblemFields() throws Exception {
        GIVEN_aTracedSyncCallable_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncCallable_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted() throws Exception {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Callable<String> childSpanB = tracer.traceCallable(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                return "test";
            });

        tracer.endTrace();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        childSpanB.call();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncCallable_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() throws Exception {
        GIVEN_aTracedSyncCallable_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncCallable_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() throws ExecutionException, InterruptedException {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Future<?> submit = Executors.newSingleThreadExecutor().submit(
            tracer.traceCallable(
                "childSpanB",
                () -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    return "test";
                }));

        tracer.endTrace();
        submit.get();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncCallable_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() throws ExecutionException, InterruptedException {
        GIVEN_aTracedAsyncCallable_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncCallable_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Future<?> submit = Executors.newSingleThreadExecutor().submit(
            tracer.traceCallable(
                "childSpanB",
                () -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    throw new IllegalStateException("Oops!");
                }));

        tracer.endTrace();
        assertThatThrownBy(submit::get).hasCauseInstanceOf(IllegalStateException.class);

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncCallable_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncCallable_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncCallable_AND_NoTraceIsActive_WHEN_runningItAndEndingTheTrace_EXPECT_nothingToHappen() throws Exception {
        final Callable<String> childSpanB = tracer.traceCallable(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().isNoop()).isTrue();
                return "test";
            });

        childSpanB.call();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncCallable_AND_NoTraceIsActive_WHEN_waitingForItToComplete_EXPECT_nothingToHappen() throws ExecutionException, InterruptedException {
        final Future<?> submit = Executors.newSingleThreadExecutor().submit(
            tracer.traceCallable(
                "childSpanB",
                () -> assertThat(tracer.getActiveSpan().isNoop()).isTrue()));

        submit.get();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    ///////////////////////////////////////////////////////////////////////////
    // TRACED SUPPLIER TESTS
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aTracedSyncSupplier_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Supplier<String> childSpanB = tracer.traceSupplier(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                return "test";
            });

        childSpanB.get();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncSupplier_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncSupplier_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncSupplier_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Supplier<String> childSpanB = tracer.traceSupplier(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                throw new IllegalStateException("Oops!");
            });

        try {
            childSpanB.get();
        } catch (IllegalStateException e) {
        }
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncSupplier_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncSupplier_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncSupplier_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Supplier<String> childSpanB = tracer.traceSupplier(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                return "test";
            });

        tracer.endTrace();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        childSpanB.get();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncSupplier_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncSupplier_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncSupplier_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<?> submit = CompletableFuture.supplyAsync(
            tracer.traceSupplier(
                "childSpanB",
                () -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    return "test";
                }));

        tracer.endTrace();
        submit.join();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncSupplier_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncSupplier_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncSupplier_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<?> submit = CompletableFuture.supplyAsync(
            tracer.traceSupplier(
                "childSpanB",
                () -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    throw new IllegalStateException("Oops!");
                }));

        tracer.endTrace();
        assertThatThrownBy(submit::join).hasCauseInstanceOf(IllegalStateException.class);

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncSupplier_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncSupplier_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncSupplier_AND_NoTraceIsActive_WHEN_runningItAndEndingTheTrace_EXPECT_nothingToHappen() {
        final Supplier<String> childSpanB = tracer.traceSupplier(
            "childSpanB",
            () -> {
                assertThat(tracer.getActiveSpan().isNoop()).isTrue();
                return "test";
            });

        childSpanB.get();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncSupplier_AND_NoTraceIsActive_WHEN_waitingForItToComplete_EXPECT_nothingToHappen() {
        final CompletableFuture<?> submit = CompletableFuture.supplyAsync(
            tracer.traceSupplier(
                "childSpanB",
                () -> assertThat(tracer.getActiveSpan().isNoop()).isTrue()));

        submit.join();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }


    ///////////////////////////////////////////////////////////////////////////
    // TRACED FUNCTION TESTS
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aTracedSyncFunction_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Function<String, String> childSpanB = tracer.traceFunction(
            "childSpanB",
            (input) -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                return input.toUpperCase();
            });

        childSpanB.apply("test");
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncFunction_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncFunction_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncFunction_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Function<String, String> childSpanB = tracer.traceFunction(
            "childSpanB",
            (input) -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                throw new IllegalStateException("Oops!");
            });

        try {
            childSpanB.apply("test");
        } catch (IllegalStateException e) {
        }
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncFunction_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncFunction_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncFunction_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Function<String, String> childSpanB = tracer.traceFunction(
            "childSpanB",
            (input) -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                return input.toUpperCase();
            });

        tracer.endTrace();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        childSpanB.apply("test");
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncFunction_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncFunction_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncFunction_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<?> submit = CompletableFuture
            .supplyAsync(() -> "test")
            .thenApplyAsync(tracer.traceFunction(
                "childSpanB",
                (input) -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    return input.toUpperCase();
                }));

        tracer.endTrace();
        submit.join();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncFunction_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncFunction_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncFunction_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<?> submit = CompletableFuture
            .supplyAsync(() -> "test")
            .thenApplyAsync(tracer.traceFunction(
                "childSpanB",
                (input) -> {
                    assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                    throw new IllegalStateException("Oops!");
                }));

        tracer.endTrace();
        assertThatThrownBy(submit::join).hasCauseInstanceOf(IllegalStateException.class);

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncFunction_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncFunction_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncFunction_AND_NoTraceIsActive_WHEN_runningItAndEndingTheTrace_EXPECT_nothingToHappen() {
        final Function<String, String> childSpanB = tracer.traceFunction(
            "childSpanB",
            (input) -> {
                assertThat(tracer.getActiveSpan().isNoop()).isTrue();
                return input.toUpperCase();
            });

        childSpanB.apply("test");
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncFunction_AND_NoTraceIsActive_WHEN_waitingForItToComplete_EXPECT_nothingToHappen() {
        final CompletableFuture<?> submit = CompletableFuture
            .supplyAsync(() -> "test")
            .thenApplyAsync(tracer.traceFunction(
                "childSpanB",
                (String input) -> {
                    assertThat(tracer.getActiveSpan().isNoop()).isTrue();
                    return input.toUpperCase();
                }));

        submit.join();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    ///////////////////////////////////////////////////////////////////////////
    // TRACED CONSUMER TEST
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aTracedSyncConsumer_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Consumer<String> childSpanB = tracer.traceConsumer(
            "childSpanB",
            (input) -> assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB"));

        childSpanB.accept("test");
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncConsumer_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncConsumer_WHEN_runningItAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncConsumer_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Consumer<String> childSpanB = tracer.traceConsumer(
            "childSpanB",
            (input) -> {
                assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                throw new IllegalStateException("Oops!");
            });

        try {
            childSpanB.accept("test");
        } catch (IllegalStateException e) {
        }
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        tracer.endTrace();
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncConsumer_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncConsumer_WHEN_runningItAndItThrowsException_EXPECT_traceToBeCorrectlyEndedAnyway();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncConsumer_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final Consumer<String> childSpanB = tracer.traceConsumer(
            "childSpanB",
            (input) -> assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB"));

        tracer.endTrace();
        verify(mockTransport, times(1)).submit(any(ResolvedEvent.class));

        childSpanB.accept("test");
        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedSyncConsumer_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedSyncConsumer_WHEN_runningItAfterTraceHasEnded_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncConsumer_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<?> submit = CompletableFuture
            .supplyAsync(() -> "test")
            .thenAcceptAsync(
                tracer.traceConsumer(
                    "childSpanB",
                    (input) -> assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB")));

        tracer.endTrace();
        submit.join();

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncConsumer_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncConsumer_WHEN_waitingForItToCompleteAndEndingTheTrace_EXPECT_twoEventsToBeSubmitted();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedAsyncConsumer_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally() {
        tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("serviceA").build());
        final CompletableFuture<?> submit = CompletableFuture
            .supplyAsync(() -> "test")
            .thenAcceptAsync(
                tracer.traceConsumer(
                    "childSpanB",
                    (input) -> {
                        assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("childSpanB");
                        throw new IllegalStateException("Oops!");
                    }));

        tracer.endTrace();
        assertThatThrownBy(submit::join).hasCauseInstanceOf(IllegalStateException.class);

        verify(mockTransport, times(2)).submit(any(ResolvedEvent.class));
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncConsumer_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally_AND_DoesNotContainProblemFields() {
        GIVEN_aTracedAsyncConsumer_WHEN_itThrowsAnException_EXPECT_traceToBeEndedNormally();

        final List<ResolvedEvent> resolvedEvents = captureAllEvents();
        assertThat(resolvedEvents).allMatch(this::doesNotContainProblemFields);
    }

    @Test
    public void GIVEN_aTracedSyncConsumer_AND_NoTraceIsActive_WHEN_runningItAndEndingTheTrace_EXPECT_nothingToHappen() {
        final Consumer<String> childSpanB = tracer.traceConsumer(
            "childSpanB",
            (input) -> assertThat(tracer.getActiveSpan().isNoop()).isTrue());

        childSpanB.accept("test");
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    @Test
    public void GIVEN_aTracedAsyncConsumer_AND_NoTraceIsActive_WHEN_waitingForItToComplete_EXPECT_nothingToHappen() {
        final CompletableFuture<?> submit = CompletableFuture
            .supplyAsync(() -> "test")
            .thenAcceptAsync(
                tracer.traceConsumer(
                    "childSpanB",
                    (input) -> assertThat(tracer.getActiveSpan().isNoop()).isTrue()));

        submit.join();
        tracer.endTrace();

        verifyZeroInteractions(mockTransport);
        assertThatNoSpanIsActive();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Thread context tests
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_anUnclosedSpan_WHEN_closingSpanOnADifferentThread_EXPECT_spanToBeSubmittedWithMETAField() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        CompletableFuture.runAsync(rootSpan::close).join();

        final ResolvedEvent resolvedEvent = captureEvent();
        assertThat(resolvedEvent.getFields()).containsEntry("meta.dirty_context", true);
    }

    @Test
    public void GIVEN_anUnclosedSpan_WHEN_closingSpanOnADifferentThread_where_threadHasAnActiveSpan_EXPECT_spanToBeSubmittedWithMETAField() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        CompletableFuture.runAsync(() -> {
            tracer.startTrace(factory.createBuilder().setSpanName("other-span").setServiceName("service").build());
            verifyZeroInteractions(mockTransport);
            rootSpan.close();
        }).join();

        final ResolvedEvent resolvedEvent = captureEvent();
        assertThat(resolvedEvent.getFields()).containsEntry("meta.dirty_context", true);
    }

    @Test
    public void GIVEN_anUnclosedSpan_WHEN_closingSpanOnADifferentThread_EXPECT_childSpansOnOriginalThreadToNotBeSent() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("root-span").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("child-span");

        CompletableFuture.runAsync(rootSpan::close).join();

        final ResolvedEvent resolvedEvent = captureEvent();
        assertThat(resolvedEvent.getFields()).containsEntry("meta.dirty_context", true);
        assertThat(resolvedEvent.getFields()).containsEntry("name", "root-span");
    }

    @Test
    public void WHEN_closingSpanOnADifferentThread_andThen_EndingTraceOnOriginalThread_EXPECT_closedSpanToNotResubmit() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("root-span").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("child-span");

        CompletableFuture.runAsync(rootSpan::close).join();

        tracer.endTrace();
        final List<ResolvedEvent> resolvedEvent = captureAllEvents();
        assertThat(resolvedEvent).hasSize(2);
    }

    @Test
    public void GIVEN_aTraceInOneThread_and_NoActiveTraceInAnotherThread_WHEN_GettingActiveSpanInOtherThread_EXPECT_spanToBeNoop() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("root-span").setServiceName("service").build());

        CompletableFuture.runAsync(() -> assertThat(tracer.getActiveSpan().isNoop()).isTrue()).join();

        verifyZeroInteractions(mockTransport);
    }

    @Test
    public void GIVEN_aTraceInOneThread_WHEN_EndingTraceInAnotherThread_EXPECT_noEffectOnOriginalThreadTrace() {
        final Span rootSpan = tracer.startTrace(factory.createBuilder().setSpanName("root-span").setServiceName("service").build());

        CompletableFuture.runAsync(() -> tracer.endTrace()).join();

        assertThat(tracer.getActiveSpan()).isSameAs(rootSpan);
        verifyZeroInteractions(mockTransport);
    }

    private <V> void assertThatEventHasFields(final Map<String, V> map) {
        verify(mockTransport).submit(eventCaptor.capture());
        final Map<String, Object> eventFields = eventCaptor.getValue().getFields();
        assertThat(eventFields).containsAllEntriesOf(map);
    }

    private void assertThatEventDoesntHaveField(final String key) {
        verify(mockTransport).submit(eventCaptor.capture());
        final Map<String, Object> eventFields = eventCaptor.getValue().getFields();
        assertThat(eventFields).doesNotContainKey(key);
    }

    private void assertThatNoSpanIsActive() {
        assertThat(tracer.getActiveSpan().getSpanName()).isEqualTo("NOOP");
    }

    private boolean doesNotContainProblemFields(final ResolvedEvent event) { // used as predicate via method ref
        return event.getFields().keySet().stream().anyMatch(fieldKey -> !problemFields.contains(fieldKey));
    }

    private ResolvedEvent captureEvent() {
        verify(mockTransport).submit(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private List<ResolvedEvent> captureAllEvents() {
        verify(mockTransport, atLeastOnce()).submit(eventCaptor.capture());
        return eventCaptor.getAllValues();
    }

    @Test
    public void WHEN_startingTraceWithNoopSpanBuilder_EXPECT_NoopSpanToBeReturned() {
        assertThat(
            tracer.startTrace(Span.getNoopInstance()).isNoop()
        ).isTrue();
    }

    @Test
    public void WHEN_DetachingNoop_EXPECT_NoopToBeReturned() {
        final Span span = tracer.popSpan(Span.getNoopInstance());

        assertThat(span).isSameAs(Span.getNoopInstance());
    }

    @Test
    public void GIVEN_AnActiveTrace_WHEN_DetachingNoop_EXPECT_NoopToBeReturned() {
        final Span originalSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        final Span span = tracer.popSpan(Span.getNoopInstance());

        assertThat(span).isSameAs(Span.getNoopInstance());
        assertThat(originalSpan).isSameAs(tracer.getActiveSpan());
        assertThat(originalSpan).isNotSameAs(span);
    }

    @Test
    public void GIVEN_anEndedTrace_WHEN_DetachingAnInvalidSpan_EXPECT_NoopToBeReturned() {
        final Span originalSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());
        tracer.endTrace();

        final Span span = tracer.popSpan(originalSpan);

        assertThat(span).isSameAs(Span.getNoopInstance());
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_DetachingActiveSpan_EXPECT_CopyToBeReturned() {
        final Span originalSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());

        final Span span = tracer.popSpan(originalSpan);

        assertThat(span).isNotSameAs(Span.getNoopInstance());
        assertThat(span).isNotSameAs(originalSpan);
        assertThat(span.getSpanId()).isSameAs(originalSpan.getSpanId());
        assertThat(span.getSpanName()).isSameAs(originalSpan.getSpanName());
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_DetachingChildSpan_EXPECT_ItsChildrenToBeSubmitted() {
        final Span originalSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("child-span");
        final Span grandChildSpan = tracer.startChildSpan("grand-child-span");
        final Span grandGrandChildSpan = tracer.startChildSpan("grand-grand-child-span");

        final Span detachedSpan = tracer.popSpan(childSpan);

        final List<ResolvedEvent> resolvedEvent = captureAllEvents();
        assertThat(resolvedEvent.get(0).getFields()).contains(
            entry("name", "grand-grand-child-span"),
            entry("meta.sent_by_parent", true)
        );
        assertThat(resolvedEvent.get(1).getFields()).contains(
            entry("name", "grand-child-span"),
            entry("meta.sent_by_parent", true)
        );
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_DetachingChildSpan_EXPECT_activeSpanToResetToParent() {
        final Span originalSpan = tracer.startTrace(factory.createBuilder().setSpanName("span").setServiceName("service").build());
        final Span childSpan = tracer.startChildSpan("child-span");
        final Span grandChildSpan = tracer.startChildSpan("grand-child-span");
        final Span grandGrandChildSpan = tracer.startChildSpan("grand-grand-child-span");

        final Span detachedSpan = tracer.popSpan(childSpan);

        assertThat(tracer.getActiveSpan()).isEqualTo(originalSpan);
        assertThat(detachedSpan.getSpanName()).isEqualTo(childSpan.getSpanName());
    }

    ///////////////////////////////////////////////////////////////////////////
    // attach span tests
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aSpan_WHEN_spanIsAttached_EXPECT_expectSpanToBeActive() {
        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").build();

        final TracerSpan tracerSpan = tracer.pushSpan(span);

        assertThat(tracerSpan.getDelegate()).isSameAs(span);
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan);
    }

    @Test
    public void GIVEN_noopSpan_WHEN_spanIsAttached_EXPECT_activeSpanToBeNoop() {
        final TracerSpan tracerSpan = tracer.pushSpan(Span.getNoopInstance());

        assertThat(tracerSpan.isNoop()).isTrue();
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan);
    }

    @Test
    public void GIVEN_noopSpan_and_normalSpan_WHEN_spansAreAttachedInSequence_EXPECT_normalSpanToBeActive() {
        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").build();

        final TracerSpan noopSpan = tracer.pushSpan(Span.getNoopInstance());
        final TracerSpan tracerSpan = tracer.pushSpan(span);

        assertThat(tracerSpan.isNoop()).isFalse();
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan);
    }

    @Test
    public void GIVEN_normalSpan_and_noopSpan_WHEN_spansAreAttachedInSequence_EXPECT_normalSpanToBeActive_and_noopSpanToBeIgnored() {
        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").build();

        final TracerSpan tracerSpan = tracer.pushSpan(span);
        final TracerSpan noopSpan = tracer.pushSpan(Span.getNoopInstance());

        assertThat(tracerSpan.isNoop()).isFalse();
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan);
    }

    @Test
    public void GIVEN_2normalSpans_WHEN_spansAreAttachedInSequence_EXPECT_secondToBeActive_and_firstToBeOnTheStack() {
        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").build();
        final Span span2 = factory.createBuilder().setSpanName("span2").setServiceName("service").build();

        final TracerSpan tracerSpan1 = tracer.pushSpan(span);
        final TracerSpan tracerSpan2 = tracer.pushSpan(span2);

        assertThat(tracerSpan1.isNoop()).isFalse();
        assertThat(tracerSpan2.isNoop()).isFalse();
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan2);

        tracerSpan2.close();
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan1);
    }

    @Test
    public void GIVEN_anActiveTrace_WHEN_spanIsAttached_EXPECT_secondToBeActive_and_firstToBeOnTheStack() {
        final Span span1 = factory.createBuilder().setSpanName("span").setServiceName("service").build();
        final TracerSpan tracerSpan1 = tracer.startTrace(span1);
        final Span span2 = factory.createBuilder().setSpanName("span2").setServiceName("service").build();

        final TracerSpan tracerSpan2 = tracer.pushSpan(span2);

        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan2);
        tracerSpan2.close();
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan1);
    }

    @Test
    public void GIVEN_anAlreadyWrappedSpan_WHEN_spanIsAttached_EXPECT_delegateToBeReWrapped() {
        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").build();
        final TracerSpan wrapperSpan = new TracerSpan(span, tracer);

        final TracerSpan tracerSpan = tracer.pushSpan(wrapperSpan);

        assertThat(tracerSpan).isNotSameAs(wrapperSpan);
        assertThat(tracerSpan.getDelegate()).isSameAs(span);
        assertThat(tracer.getActiveSpan()).isSameAs(tracerSpan);
    }

    @Test
    public void GIVEN_aSeriesOfAttachedSpan_WHEN_closingRoot_EXPECT_allSpansToBeCleared() {
        final Span span1 = factory.createBuilder().setSpanName("span1").setServiceName("service").build();
        final Span span2 = factory.createBuilder().setSpanName("span2").setServiceName("service").build();
        final Span span3 = factory.createBuilder().setSpanName("span3").setServiceName("service").build();
        final TracerSpan tracerSpan1 = tracer.pushSpan(span1);
        final TracerSpan tracerSpan2 = tracer.pushSpan(span2);
        final TracerSpan tracerSpan3 = tracer.pushSpan(span3);

        tracerSpan1.close();

        assertThat(tracer.getActiveSpan().isNoop()).isTrue();
    }
}
