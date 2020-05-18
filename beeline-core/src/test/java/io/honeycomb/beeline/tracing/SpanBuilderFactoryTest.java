package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.ids.UUIDTraceIdProvider;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import io.honeycomb.libhoney.transport.batch.impl.SystemClockProvider;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class SpanBuilderFactoryTest {
    private SpanBuilderFactory factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.alwaysSampler());

    @Test
    public void WHEN_copyingANoopSpan_EXPECT_returnOfNoopBuilderAndSpan() {
        final SpanBuilderFactory.SpanBuilder build = factory.createBuilderFrom(Span.getNoopInstance());

        assertThat(build).isInstanceOf(SpanBuilderFactory.NoopSpanBuilder.class);

        assertThat(build.build()).isSameAs(Span.getNoopInstance());
    }

    @Test
    public void WHEN_creatingAChildOfNoopSpan_EXPECT_returnOfNoopBuilderAndSpan() {
        final SpanBuilderFactory.SpanBuilder builder = factory.createBuilderFromParent(Span.getNoopInstance());

        assertThat(builder).isInstanceOf(SpanBuilderFactory.NoopSpanBuilder.class);

        assertThat(builder.build()).isSameAs(Span.getNoopInstance());
    }

    @Test
    public void WHEN_copyingASpan_EXPECT_copyToContainSameAttributesAsOriginal() {
        final PropagationContext context = new PropagationContext("123", "abc", null, Collections.singletonMap("key", "value"));
        final Span original = factory.createBuilder().setSpanName("span").setServiceName("service").setParentContext(context).build();

        final Span copy = factory.createBuilderFrom(original).build();

        assertThat(copy.getParentSpanId()).isEqualTo("abc");
        assertThat(copy.getTraceId()).isEqualTo("123");
        assertThat(copy.getSpanId()).isEqualTo(original.getSpanId());
        assertThat(copy.getSpanName()).isEqualTo("span");
        assertThat(copy.getServiceName()).isEqualTo("service");
        assertThat(copy.elapsedTimeMs()).isGreaterThan(0.0);
        assertThat(copy.getTimestamp()).isGreaterThan(0L);
        assertThat(copy.getTraceContext().getTraceFields()).containsEntry("key", "value");
        assertThat(copy.getTraceContext().getTraceId()).isEqualTo("123");
        assertThat(copy.getTraceContext().getSpanId()).isEqualTo(original.getSpanId());
    }

    @Test
    public void WHEN_copyingASpan_EXPECT_copyToContainSameFieldsAsOriginal() {
        final PropagationContext context = new PropagationContext("123", "abc", null, Collections.singletonMap("key", "value"));
        final Span original = factory.createBuilder().setSpanName("span").setServiceName("service").setParentContext(context).build();
        original.addField("field1", "value1");
        original.addField("field2", "value2");

        final Span copy = factory.createBuilderFrom(original).build();

        assertThat(copy.getFields()).contains(entry("field1", "value1"), entry("field2", "value2"));
    }

    @Test
    public void WHEN_buildingASpan_EXPECT_initialValues() {
        final SpanBuilderFactory.SpanBuilder builder = factory.createBuilder();

        assertThat(builder.getSpanName()).isNull();
        assertThat(builder.getServiceName()).isNull();
        assertThat(builder.getProcessor()).isNotNull();
        assertThat(builder.getTimestamp()).isNull();
        assertThat(builder.getStartTime()).isNull();
        assertThat(builder.getFields()).isNotNull().isEmpty();
        assertThat(builder.getSpanId()).isNull();
        assertThat(builder.getClock()).isSameAs(SystemClockProvider.getInstance());
        assertThat(builder.getParentContext()).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_ASpanBuilder_WHEN_settingAttributesBuildingASpan_EXPECT_initialValues() {
        final PropagationContext context = new PropagationContext("123", "abc", null, Collections.singletonMap("key", "value"));
        final SpanBuilderFactory.SpanBuilder builder = factory.createBuilder()
            .setSpanName("span")
            .setServiceName("service")
            .setTimes(1L, 2L)
            .setParentContext(context)
            .addField("field", "value")
            .addFields(Collections.singletonMap("key", "value"));

        assertThat(builder.getSpanName()).isEqualTo("span");
        assertThat(builder.getServiceName()).isEqualTo("service");
        assertThat(builder.getTimestamp()).isEqualTo(1L);
        assertThat(builder.getStartTime()).isEqualTo(2L);
        assertThat(builder.getFields()).containsExactly(entry("field", "value"), entry("key", "value"));
        assertThat(builder.getParentContext()).isEqualTo(context);
    }


    @Test
    public void WHEN_creatingASpanWithoutRequiredAttributes_EXPECT_IAEToBeThrown() {
        assertThatThrownBy(() -> factory.createBuilder().setSpanName("span").setServiceName("").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.createBuilder().setSpanName("").setServiceName("service").build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void WHEN_creatingAChildSpanWithoutRequiredAttributes_EXPECT_IAEToBeThrown() {
        final Span testSpan = getTestSpan();

        assertThatThrownBy(() -> factory.createBuilderFromParent(testSpan).setSpanName("").build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void GIVEN_ASpanBuilder_WHEN_settingAnExplicitTimes_EXPECT_StartAndTimestampToBeSetOnSpan() {
        final Span newSpan = factory.createBuilder()
            .setSpanName("span")
            .setServiceName("service")

            .setTimes(1L, 2L)
            .build();

        assertThat(newSpan.getStartTime()).isEqualTo(2L);
        assertThat(newSpan.getTimestamp()).isEqualTo(1L);
    }

    @Test
    public void GIVEN_ASpanBuilder_WHEN_settingAnExplicitSpanId_EXPECT_IdToBeSetOnSpan() {
        final Span newSpan = factory.createBuilder()
            .setSpanName("span")
            .setServiceName("service")

            .setSpanId("span-id")
            .build();

        assertThat(newSpan.getSpanId()).isEqualTo("span-id");
    }

    @Test
    public void GIVEN_ContextDoesNotHaveIds_WHEN_creatingASpan_EXPECT_attributesToBePresentOnInstance() {
        final PropagationContext context = new PropagationContext(null, null, null, Collections.singletonMap("key", "value"));

        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").setParentContext(context).build();

        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.getTraceId()).satisfies(UUID::fromString);
        assertThat(span.getSpanId()).satisfies(UUID::fromString);
        assertThat(span.getSpanName()).isEqualTo("span");
        assertThat(span.getServiceName()).isEqualTo("service");
        assertThat(span.elapsedTimeMs()).isGreaterThan(0.0);
        assertThat(span.getTraceContext().getTraceFields()).containsEntry("key", "value");
        assertThat(span.getTraceContext().getTraceId()).satisfies(UUID::fromString); // checks that it's a valid UUID string
        assertThat(span.getTraceContext().getSpanId()).isEqualTo(span.getSpanId());
    }

    @Test
    public void GIVEN_AFullContext_WHEN_creatingASpan_EXPECT_attributesToBePresentOnInstance() {
        final PropagationContext context = new PropagationContext("123", "abc", null, Collections.singletonMap("key", "value"));

        final Span span = factory.createBuilder().setSpanName("span").setServiceName("service").setParentContext(context).build();

        assertThat(span.getParentSpanId()).isEqualTo("abc");
        assertThat(span.getTraceId()).isEqualTo("123");
        assertThat(span.getSpanId()).satisfies(UUID::fromString);
        assertThat(span.getSpanName()).isEqualTo("span");
        assertThat(span.getServiceName()).isEqualTo("service");
        assertThat(span.elapsedTimeMs()).isGreaterThan(0.0);
        assertThat(span.getTraceContext().getTraceFields()).containsEntry("key", "value");
        assertThat(span.getTraceContext().getTraceId()).isEqualTo("123");
        assertThat(span.getTraceContext().getSpanId()).isEqualTo(span.getSpanId());
    }

    @Test
    public void GIVEN_aParentSpan_WHEN_creatingAChildSpan_EXPECT_attributesToBePresentOnChildInstance() {
        final PropagationContext context = new PropagationContext("123", "abc", null, Collections.singletonMap("key", "value"));
        final Span parentSpan = factory.createBuilder().setSpanName("span").setServiceName("service").setParentContext(context).build();

        final Span childSpan = factory.createBuilderFromParent(parentSpan).setSpanName("new-span").build();

        assertThat(childSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(childSpan.getTraceId()).isEqualTo("123");
        assertThat(childSpan.getSpanId()).satisfies(UUID::fromString);
        assertThat(childSpan.getSpanName()).isEqualTo("new-span");
        assertThat(childSpan.getServiceName()).isEqualTo("service");
        assertThat(childSpan.elapsedTimeMs()).isGreaterThan(0.0);
        assertThat(childSpan.getTraceContext().getTraceFields()).containsEntry("key", "value");
        assertThat(childSpan.getTraceContext().getTraceId()).isEqualTo("123");
        assertThat(childSpan.getTraceContext().getSpanId()).isEqualTo(childSpan.getSpanId());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests for specific types
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aSendingSpan_WHEN_creatingAChildSpanFromASendingSpan_EXPECT_attributesToBePresentOnChildInstance() {
        final Span testSpan = getTestSpan();

        final Span childSpan = factory.createBuilderFromParent(testSpan).setSpanName("new-span").build();

        assertThat(childSpan.getParentSpanId()).isEqualTo(testSpan.getSpanId());
        assertThat(childSpan.getTraceId()).isEqualTo(testSpan.getTraceId());
        assertThat(childSpan.getSpanId()).satisfies(UUID::fromString);
        assertThat(childSpan.getSpanName()).isEqualTo("new-span");
        assertThat(childSpan.getServiceName()).isEqualTo(testSpan.getServiceName());
        assertThat(childSpan.elapsedTimeMs()).isGreaterThan(0.0);
        assertThat(childSpan.getTraceContext().getTraceFields()).containsExactly(entry("key2", "value2"));
        assertThat(childSpan.getTraceContext().getTraceId()).isEqualTo(testSpan.getTraceId());
        assertThat(childSpan.getTraceContext().getSpanId()).isEqualTo(childSpan.getSpanId());
    }

    @Test
    public void GIVEN_aSendingSpan_WHEN_creatingACopyFromASendingSpan_EXPECT_attributesToBePresentOnCopyInstance() {
        final Span testSpan = getTestSpan();

        final Span copy = factory.createBuilderFrom(testSpan).build();

        assertThat(copy.getParentSpanId()).isEqualTo(testSpan.getParentSpanId());
        assertThat(copy.getTraceId()).isEqualTo(testSpan.getTraceId());
        assertThat(copy.getSpanId()).isEqualTo(testSpan.getSpanId());
        assertThat(copy.getSpanName()).isEqualTo(testSpan.getSpanName());
        assertThat(copy.getServiceName()).isEqualTo(testSpan.getServiceName());
        assertThat(copy.getTraceContext().getTraceId()).isEqualTo(testSpan.getTraceId());
        assertThat(copy.getTraceContext().getSpanId()).isEqualTo(testSpan.getSpanId());
        assertThat(copy.getFields()).isEqualTo(testSpan.getFields());
        assertThat(copy.getTraceFields()).isEqualTo(testSpan.getTraceFields());
    }

    @Test
    public void GIVEN_aTracerSpan_WHEN_creatingAChildSpanFromATracerSpan_EXPECT_attributesToBePresentOnChildInstance() {
        final Span testSpan = getTestSpan();
        final TracerSpan tracerSpan = new TracerSpan(testSpan, mock(Tracer.class));

        final Span childSpan = factory.createBuilderFromParent(tracerSpan).setSpanName("new-span").build();

        assertThat(childSpan.getParentSpanId()).isEqualTo(tracerSpan.getSpanId());
        assertThat(childSpan.getTraceId()).isEqualTo(tracerSpan.getTraceId());
        assertThat(childSpan.getSpanId()).satisfies(UUID::fromString);
        assertThat(childSpan.getSpanName()).isEqualTo("new-span");
        assertThat(childSpan.getServiceName()).isEqualTo(tracerSpan.getServiceName());
        assertThat(childSpan.elapsedTimeMs()).isGreaterThan(0.0);
        assertThat(childSpan.getTraceContext().getTraceId()).isEqualTo(tracerSpan.getTraceId());
        assertThat(childSpan.getTraceContext().getSpanId()).isEqualTo(childSpan.getSpanId());
    }

    @Test
    public void GIVEN_aTracerSpan_WHEN_creatingACopyFromATracerSpan_EXPECT_attributesToBePresentOnCopyInstance() {
        final Span testSpan = getTestSpan();
        final TracerSpan tracerSpan = new TracerSpan(testSpan, mock(Tracer.class));

        final Span copy = factory.createBuilderFrom(tracerSpan).build();

        assertThat(copy.getParentSpanId()).isEqualTo(tracerSpan.getParentSpanId());
        assertThat(copy.getTraceId()).isEqualTo(tracerSpan.getTraceId());
        assertThat(copy.getSpanId()).isEqualTo(tracerSpan.getSpanId());
        assertThat(copy.getSpanName()).isEqualTo(tracerSpan.getSpanName());
        assertThat(copy.getServiceName()).isEqualTo(tracerSpan.getServiceName());
        assertThat(copy.getTraceContext().getTraceId()).isEqualTo(tracerSpan.getTraceId());
        assertThat(copy.getTraceContext().getSpanId()).isEqualTo(tracerSpan.getSpanId());
        assertThat(copy.getFields()).isEqualTo(tracerSpan.getFields());
        assertThat(copy.getTraceFields()).isEqualTo(tracerSpan.getTraceFields());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Sampling Tests
    ///////////////////////////////////////////////////////////////////////////
    @Test
    public void GIVEN_aFactoryThatNeverSamplesSpans_WHEN_buildingASpan_EXPECT_returnedSpanToBeNoop() {
        factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.neverSampler());

        final Span rootSpan = factory.createBuilder().setSpanName("span").setServiceName("service").build();

        assertThat(rootSpan.isNoop()).isTrue();
    }

    @Test
    public void GIVEN_aFactoryThatNeverSamplesSpans_WHEN_buildingAChildSpan_EXPECT_returnedSpanToBeNoop() {
        final Span testSpan = getTestSpan();
        factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.neverSampler());

        final Span span = factory.createBuilderFromParent(testSpan).setSpanName("span").build();

        assertThat(span.isNoop()).isTrue();
    }

    @Test
    public void GIVEN_aFactoryThatAlwaysSamplesSpans_WHEN_buildingAChildSpan_EXPECT_sampleRateToBeZero() {
        final Span testSpan = getTestSpan();
        factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.alwaysSampler());

        final SendingSpan span = (SendingSpan) factory.createBuilderFromParent(testSpan).setSpanName("span").build();

        assertThat(span.getInitialSampleRate()).isEqualTo(1);
    }

    private SendingSpan getTestSpan() {
        return new SendingSpan(
            "span", "service", "spanId", Collections.singletonMap("key1", "value1"),
            new PropagationContext("parentSpanId", "traceId", null, Collections.singletonMap("key2", "value2")),
            mock(SpanPostProcessor.class), mock(ClockProvider.class), 1
        );
    }

    @Test
    public void GIVEN_aFactoryThatNeverSamplesSpans_WHEN_passingANoopSpanToChildSpanMethod_EXPECT_returnedSpanToBeNoop() {
        factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.neverSampler());

        final Span span = factory.createBuilderFromParent(Span.getNoopInstance()).setSpanName("span").build();

        assertThat(span.isNoop()).isTrue();
    }


    @Test
    public void GIVEN_aFactoryThatNeverSamplesSpans_WHEN_creatingACopySpan_EXPECT_returnedSpanToBeNoop() {
        factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.neverSampler());

        final Span span = factory.createBuilderFrom(getTestSpan()).build();

        assertThat(span.isNoop()).isTrue();
    }

    @Test
    public void GIVEN_aFactoryThatNeverSamplesSpans_WHEN_passingANoopSpanToCopyMethod_EXPECT_returnedSpanToBeNoop() {
        factory = new SpanBuilderFactory(mock(SpanPostProcessor.class), SystemClockProvider.getInstance(), UUIDTraceIdProvider.getInstance(), Sampling.neverSampler());

        final Span span = factory.createBuilderFrom(Span.getNoopInstance()).build();

        assertThat(span.isNoop()).isTrue();
    }

    @Test
    public void GIVEN_aFactory_WHEN_closed_EXEPECT_factoryToDelegateToSpanPostProcessor() {
        factory.close();

        final SpanPostProcessor mock = factory.getProcessor();
        verify(mock, times(1)).close();
        verifyNoMoreInteractions(mock);
    }
}
