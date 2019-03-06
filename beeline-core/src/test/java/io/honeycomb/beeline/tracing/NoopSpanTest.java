package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


public class NoopSpanTest {

    @Test
    public void checkThatMultipleInvocationsOfGetInstanceReturnTheSameInstance() {
        assertThat(Span.getNoopInstance()).isSameAs(Span.getNoopInstance());
    }

    @Test
    public void checkThatAManuallyBuiltSpanWhichIsAlwaysSampledIsNotANoopSpan() {
        final Span span = Tracing.createSpanBuilderFactory(mock(SpanPostProcessor.class), Sampling.alwaysSampler()).createBuilder().setSpanName("span").setServiceName("service").build();

        assertThat(span.isNoop()).isFalse();
    }

    @Test
    public void checkThatAManuallyBuiltSpanWhichIsNeverSamplerIsANoopSpan() {
        final Span span = Tracing.createSpanBuilderFactory(mock(SpanPostProcessor.class), Sampling.neverSampler()).createBuilder().setSpanName("span").setServiceName("service").build();

        assertThat(span.isNoop()).isTrue();
    }

    @Test
    public void checkThatSpansEmptyConstructorIntialisesToNoop() {
        final Span span = new Span();

        assertThat(span.isNoop()).isTrue();
    }

    @Test
    public void checkThatSendingSpanIsNotANoop() {
        final SpanPostProcessor mockProcessor = mock(SpanPostProcessor.class);
        final ClockProvider mockClock = mock(ClockProvider.class);
        final Span span = new SendingSpan("span", "service", "spanID", emptyMap(), new PropagationContext("parentSpanId", "traceID", null, emptyMap()), mockProcessor, mockClock, 1);

        assertThat(span.isNoop()).isFalse();
    }

    @Test
    public void checkThatTracerSpanIsNotNoop() {
        final SpanPostProcessor mockProcessor = mock(SpanPostProcessor.class);
        final ClockProvider mockClock = mock(ClockProvider.class);
        final Span span = new TracerSpan(mock(Span.class), mock(Tracer.class));

        assertThat(span.isNoop()).isFalse();
    }

    @Test
    public void checkThatANoopSpanIsANoopSpan() {
        final Span noop = Span.getNoopInstance();

        assertThat(noop.isNoop()).isTrue();
    }

    @Test
    public void checkInitialValuesOfNoopSpan() {
        final Span noop = Span.getNoopInstance();

        assertThatAllAttributesAreNoop(noop);
    }

    @Test
    public void checkThatInitialValuesRemainUnchangedWhenCallingMutatorMethods() {
        final Span noop = Span.getNoopInstance();

        noop.addField("key", "value");
        noop.markStart();
        noop.addFields(Collections.singletonMap("otherKey", "otherValue"));
        noop.addTraceFields(Collections.singletonMap("otherTraceKey", "otherTraceValue"));
        noop.addTraceField("traceKey", "traceValue");

        assertThatAllAttributesAreNoop(noop);
    }

    @Test
    public void checkSetStartMutator() {
        final Span noop = Span.getNoopInstance();

        noop.markStart(50, 60);

        assertThat(noop.getTimestamp()).isNotEqualTo(50);
        assertThat(noop.getStartTime()).isNotEqualTo(60);
    }

    @Test
    public void checkThatCloseHasNoEffect() {
        final Span noop = Span.getNoopInstance();

        noop.close();

        assertThatAllAttributesAreNoop(noop);
    }

    @Test
    public void EXPECT_closeToOnlyInvokeCloseInternalOnce() {
        final AtomicInteger counter = new AtomicInteger();
        Span span = new TestSpan(counter, false);

        span.close();
        span.close();
        assertThat(counter).hasValue(1);
    }

    @Test
    public void EXPECT_closeToNotInvokeCloseInternalIfNoop() {
        final AtomicInteger counter = new AtomicInteger();
        Span span = new TestSpan(counter, true);

        span.close();
        span.close();
        assertThat(counter).hasValue(0);
    }

    @Test
    public void checkToString() {
        final AtomicInteger counter = new AtomicInteger();
        Span span = new TestSpan(counter, true);

        assertThat(span.toString()).isNotBlank();
    }

    private void assertThatAllAttributesAreNoop(final Span instance) {
        assertThat(instance.getSpanId()).isEqualTo("NOOP");
        assertThat(instance.getSpanName()).isEqualTo("NOOP");
        assertThat(instance.getServiceName()).isEqualTo("NOOP");
        assertThat(instance.getTraceId()).isEqualTo("NOOP");
        assertThat(instance.getParentSpanId()).isEqualTo("NOOP");

        assertThat(instance.getFields()).isEqualTo(emptyMap());
        assertThat(instance.getTraceContext()).isEqualTo(PropagationContext.emptyContext());
        assertThat(instance.getTraceFields()).isEqualTo(emptyMap());
    }

    private static class TestSpan extends Span {
        private final AtomicInteger counter;
        private final boolean isNoop;

        public TestSpan(final AtomicInteger counter, final boolean isNoop) {
            this.counter = counter;
            this.isNoop = isNoop;
        }

        @Override
        public boolean isNoop() {
            return isNoop;
        }

        @Override
        public void close() {
            super.close();
        }

        @Override
        protected void closeInternal() {
            counter.incrementAndGet();
            super.closeInternal();
        }
    }
}
