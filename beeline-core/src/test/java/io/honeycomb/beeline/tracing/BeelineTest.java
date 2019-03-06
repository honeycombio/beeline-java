package io.honeycomb.beeline.tracing;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class BeelineTest {

    @Test
    public void GIVEN_aBeelineConstructedWithArguments_EXPECT_gettersToReturnSameArguments() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);

        final Beeline beeline = new Beeline(tracer, factory);

        assertThat(beeline.getTracer()).isEqualTo(tracer);
        assertThat(beeline.getSpanBuilderFactory()).isEqualTo(factory);
        verifyZeroInteractions(tracer, factory);
    }

    @Test
    public void GIVEN_beeline_WHEN_gettingActiveSpan_EXPECT_beelineToDelegateToTracer() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final Beeline beeline = new Beeline(tracer, factory);

        beeline.getActiveSpan();

        verify(tracer).getActiveSpan();
        verifyNoMoreInteractions(tracer, factory);
    }

    @Test
    public void GIVEN_beeline_WHEN_startingChild_EXPECT_beelineToDelegateToTracer() {
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final Tracer tracer = mock(Tracer.class);
        final Beeline beeline = new Beeline(tracer, factory);

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
        final Beeline beeline = new Beeline(tracer, factory);

        beeline.addField("key", "value");

        verify(tracer).getActiveSpan();
        verify(mockSpan).addField("app.key", "value");
        verifyNoMoreInteractions(tracer, factory, mockSpan);
    }
}
