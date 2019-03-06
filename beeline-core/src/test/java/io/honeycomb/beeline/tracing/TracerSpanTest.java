package io.honeycomb.beeline.tracing;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class TracerSpanTest {


    private Tracer mockTracer;
    private Span mockSpan;

    @Before
    public void setUp() throws Exception {
        mockSpan = mock(Span.class);
        mockTracer = mock(Tracer.class);
    }


    @Test
    public void WHEN_gettingNoopTracerSpan_EXPECT_thatItIsNoop() {
        assertThat(TracerSpan.getNoopTracerSpan().isNoop()).isTrue();

        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_creatingNormalTracerSpan_EXPECT_thatTracerSpanIsNotNoop() {
        when(mockSpan.isNoop()).thenReturn(false);

        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        assertThat(tracerSpan.isNoop()).isFalse();
        verify(mockSpan).isNoop();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_creatingNormalTracerSpan_and_delegateIsNoop_EXPECT_thatTracerSpanIsAlsoNoop() {
        when(mockSpan.isNoop()).thenReturn(true);

        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        assertThat(tracerSpan.isNoop()).isTrue();
        verify(mockSpan).isNoop();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_creatingTracerSpan_EXPECT_toContainDelegate() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        assertThat(tracerSpan.getDelegate()).isSameAs(mockSpan);
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_addingAField_EXPECT_delegateToReceiveField() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.addField("key", "value");

        verify(mockSpan).addField("key", "value");
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_addingAFields_EXPECT_delegateToReceiveFields() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.addFields(Collections.singletonMap("key", "value"));

        verify(mockSpan).addFields(Collections.singletonMap("key", "value"));
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_addingATraceField_EXPECT_delegateToReceiveField() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.addTraceField("key", "value");

        verify(mockSpan).addTraceField("key", "value");
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_addingATraceFields_EXPECT_delegateToReceiveFields() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.addTraceFields(Collections.singletonMap("key", "value"));

        verify(mockSpan).addTraceFields(Collections.singletonMap("key", "value"));
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingFields_EXPECT_delegateToReturnFields() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getFields();

        verify(mockSpan).getFields();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingTraceFields_EXPECT_delegateToReturnFields() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getTraceFields();

        verify(mockSpan).getTraceFields();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_callingMarkStartWithArguments_EXPECT_delegateToReceiveCall() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.markStart(1L, 2L);

        verify(mockSpan).markStart(1L, 2L);
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_callingMarkStart_EXPECT_delegateToReceiveCall() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.markStart();

        verify(mockSpan).markStart();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingParentId_EXPECT_delegateToReturnParentId() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getParentSpanId();

        verify(mockSpan).getParentSpanId();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingTraceId_EXPECT_delegateToReturnTraceId() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getTraceId();

        verify(mockSpan).getTraceId();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingSpanId_EXPECT_delegateToReturnSpanId() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getSpanId();

        verify(mockSpan).getSpanId();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingSpanName_EXPECT_delegateToReturnSpanName() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getSpanName();

        verify(mockSpan).getSpanName();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingServiceName_EXPECT_delegateToReturnServiceName() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getServiceName();

        verify(mockSpan).getServiceName();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingTraceContext_EXPECT_delegateToReturnTraceContext() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getTraceContext();

        verify(mockSpan).getTraceContext();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingElapsedTime_EXPECT_delegateToReturnElapsedTime() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.elapsedTimeMs();

        verify(mockSpan).elapsedTimeMs();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingTimestamp_EXPECT_delegateToReturnTimestamp() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getTimestamp();

        verify(mockSpan).getTimestamp();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_gettingStartTime_EXPECT_delegateToReturnStartTime() {
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.getStartTime();

        verify(mockSpan).getStartTime();
        verifyNoMoreInteractions(mockSpan, mockTracer);
    }

    @Test
    public void WHEN_closingNoopSpan_EXPECT_noInteractions() {
        when(mockSpan.isNoop()).thenReturn(true);
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.close();

        verify(mockSpan).isNoop();
        verifyZeroInteractions(mockTracer, mockSpan);
    }

    @Test
    public void WHEN_closingSpan_EXPECT_delegateToBeClosed() {
        final TracerSpan otherMockSpan = mock(TracerSpan.class);
        when(mockTracer.getActiveSpan()).thenReturn(otherMockSpan);
        when(mockSpan.getTraceId()).thenReturn("trace-123");
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.close();

        verify(mockSpan).close();
    }

    @Test
    public void WHEN_doubleClosingSpan_EXPECT_delegateToBeClosedOnceOnly() {
        final TracerSpan otherMockSpan = mock(TracerSpan.class);
        when(mockTracer.getActiveSpan()).thenReturn(otherMockSpan);
        when(mockSpan.getTraceId()).thenReturn("trace-123");
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.close();
        tracerSpan.close();

        verify(mockSpan).close();
    }

    @Test
    public void GIVEN_aTracerWIthSpansFromTheSameTrace_WHEN_checkingIfFromCurrentTrace_EXPECT_toBeTrue() {
        final TracerSpan otherMockSpan = mock(TracerSpan.class);
        when(mockSpan.getTraceId()).thenReturn("trace-123");
        when(otherMockSpan.getTraceId()).thenReturn("trace-123");
        when(mockTracer.getActiveSpan()).thenReturn(otherMockSpan);

        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);
        assertThat(tracerSpan.isFromCurrentTrace()).isTrue();

        verify(mockSpan).getTraceId();
        verify(mockTracer).getActiveSpan();

    }

    @Test
    public void GIVEN_aTracerWIthSpansFromTheDifferentTrace_WHEN_checkingIfFromCurrentTrace_EXPECT_toBeFalse() {
        final TracerSpan otherMockSpan = mock(TracerSpan.class);
        when(mockSpan.getTraceId()).thenReturn("trace-123");
        when(otherMockSpan.getTraceId()).thenReturn("trace-987");
        when(mockTracer.getActiveSpan()).thenReturn(otherMockSpan);

        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);
        assertThat(tracerSpan.isFromCurrentTrace()).isFalse();

        verify(mockSpan).getTraceId();
        verify(mockTracer).getActiveSpan();

    }


    @Test
    public void WHEN_closingSpan_EXPECT_spanToBeDetachedFromTracer() {
        final TracerSpan otherMockSpan = mock(TracerSpan.class);
        when(mockSpan.getTraceId()).thenReturn("trace-123");
        when(otherMockSpan.getTraceId()).thenReturn("trace-123");
        when(mockTracer.getActiveSpan()).thenReturn(otherMockSpan);
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        tracerSpan.close();

        verify(mockSpan).close();
        verify(mockTracer).popSpan(tracerSpan);
    }

    @Test
    public void WHEN_closingSpanOnAnotherThread_EXPECT_spanNotToBeDetachedFromTracer_and_HasAdditionalField() throws InterruptedException {
        final TracerSpan otherMockSpan = mock(TracerSpan.class);
        when(mockSpan.getTraceId()).thenReturn("trace-123");
        when(otherMockSpan.getTraceId()).thenReturn("trace-123");
        when(mockTracer.getActiveSpan()).thenReturn(otherMockSpan);
        final TracerSpan tracerSpan = new TracerSpan(mockSpan, mockTracer);

        final Thread thread = new Thread(tracerSpan::close);

        thread.start();
        thread.join(10_000);

        verify(mockSpan).addField("meta.dirty_context", true);
        verify(mockSpan).close();
        verifyNoMoreInteractions(mockTracer);
    }

}
