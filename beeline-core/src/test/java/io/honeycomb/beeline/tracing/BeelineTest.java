package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.builder.BeelineBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class BeelineTest {

    @Test
    public void GIVEN_aBeelineConstructedWithArguments_EXPECT_gettersToReturnSameArguments() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final Beeline beeline = new BeelineBuilder().tracer(tracer).spanBuilderFactory(factory).build();

        assertThat(beeline.getTracer()).isEqualTo(tracer);
        assertThat(beeline.getSpanBuilderFactory()).isEqualTo(factory);
        verifyZeroInteractions(tracer, factory);
    }

    @Test
    public void GIVEN_beeline_WHEN_gettingActiveSpan_EXPECT_beelineToDelegateToTracer() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final Beeline beeline = new BeelineBuilder().tracer(tracer).spanBuilderFactory(factory).build();

        beeline.getActiveSpan();

        verify(tracer).getActiveSpan();
        verifyNoMoreInteractions(tracer, factory);
    }

    @Test
    public void GIVEN_beeline_WHEN_startingChild_EXPECT_beelineToDelegateToTracer() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final Beeline beeline = new BeelineBuilder().tracer(tracer).spanBuilderFactory(factory).build();

        beeline.startChildSpan("child");

        verify(tracer).startChildSpan("child");
        verifyNoMoreInteractions(tracer, factory);
    }

    @Test
    public void GIVEN_beeline_WHEN_addingField_EXPECT_beelineToAddNamespaceAndDelegateToTracerSpan() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final TracerSpan mockSpan = mock(TracerSpan.class);
        when(tracer.getActiveSpan()).thenReturn(mockSpan);
        final Beeline beeline = new BeelineBuilder().tracer(tracer).spanBuilderFactory(factory).build();

        beeline.addField("key", "value");

        verify(tracer).getActiveSpan();
        verify(mockSpan).addField("app.key", "value");
        verifyNoMoreInteractions(tracer, factory, mockSpan);
    }

    @Test
    public void GIVEN_beeline_WHEN_addingFieldWithAppPrefix_EXPECT_beelineToNotAddNamespace() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final TracerSpan mockSpan = mock(TracerSpan.class);
        when(tracer.getActiveSpan()).thenReturn(mockSpan);
        final Beeline beeline = new BeelineBuilder().tracer(tracer).spanBuilderFactory(factory).build();

        beeline.addField("app.key", "value");

        verify(tracer).getActiveSpan();
        verify(mockSpan).addField("app.key", "value");
        verifyNoMoreInteractions(tracer, factory, mockSpan);
    }

    @Test
    public void GIVEN_beeline_WHEN_closing_EXPECT_beelineToDelegateToSpanBuilderFactory() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final Beeline beeline = new Beeline(tracer, factory);

        beeline.close();

        verify(factory).close();
        verifyNoMoreInteractions(tracer, factory);
    }
}
