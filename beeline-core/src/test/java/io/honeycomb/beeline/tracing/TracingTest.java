package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.transport.batch.impl.SystemClockProvider;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class TracingTest {
    @Test
    public void checkThatCreateTracerWorks() {
        final Tracer tracer = Tracing.createTracer(mock(SpanBuilderFactory.class));

        assertThat(tracer).isNotNull();
    }

    @Test
    public void checkSpanBuilderFactoryFields() {
        final SpanPostProcessor mock = mock(SpanPostProcessor.class);
        final SpanBuilderFactory factory = Tracing.createSpanBuilderFactory(mock, Sampling.alwaysSampler());

        assertThat(factory.getProcessor()).isSameAs(mock);
        assertThat(factory.getSampler()).isSameAs(Sampling.alwaysSampler());
        assertThat(factory.getClock()).isSameAs(SystemClockProvider.getInstance());
        assertThat(factory.generateId()).satisfies(UUID::fromString);
    }

    @Test
    public void checkThatCreateBeelineWorks() {
        final Tracer tracer = mock(Tracer.class);
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Beeline beeline = Tracing.createBeeline(tracer, factory);

        assertThat(beeline.getSpanBuilderFactory()).isEqualTo(factory);
        assertThat(beeline.getTracer()).isEqualTo(tracer);
    }

    @Test
    public void checkThatCreateSpanProcessorWorks() {
        final SpanPostProcessor processor = Tracing.createSpanProcessor(mock(HoneyClient.class), Sampling.alwaysSampler());

        assertThat(processor).isNotNull();
    }
}
