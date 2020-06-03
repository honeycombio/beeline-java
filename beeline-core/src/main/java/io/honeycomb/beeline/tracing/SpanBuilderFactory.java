package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.ids.TraceIdProvider;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import io.honeycomb.libhoney.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * <h1>Sampling</h1>
 * See the {@link Beeline} javadoc for details on sampling.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared. The SpanBuilder that it creates is not.
 */
public class SpanBuilderFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SpanBuilderFactory.class);

    private final SpanPostProcessor processor;
    private final ClockProvider clock;
    private final TraceIdProvider idProvider;
    private final TraceSampler<? super String> globalSampler;
    private final NoopSpanBuilder noopSpanBuilder;

    public SpanBuilderFactory(final SpanPostProcessor processor,
                              final ClockProvider clock,
                              final TraceIdProvider idProvider,
                              final TraceSampler<? super String> globalSampler) {
        Assert.notNull(processor, "Validation failed: processor is required");
        Assert.notNull(clock, "Validation failed: clock is required");
        Assert.notNull(idProvider, "Validation failed: idProvider is required");
        Assert.notNull(globalSampler, "Validation failed: globalSampler is required");

        this.processor = processor;
        this.clock = clock;
        this.idProvider = idProvider;
        this.globalSampler = globalSampler;
        this.noopSpanBuilder = new NoopSpanBuilder(processor, clock, idProvider, globalSampler);
    }

    /**
     * Creates a SpanBuilder instance that allows the customisation of Spans with various attributes.
     * This method is useful when Spans need to be handled manually, typically for custom framework instrumentation.
     * <p>
     * The returned builder must be configured with at least:
     * <ul>
     * <li>spanName</li>
     * <li>serviceName</li>
     * </ul>
     *
     * @return a new Span.
     */
    public SpanBuilder createBuilder() {
        return new SpanBuilder(processor, clock, idProvider, globalSampler);
    }

    /**
     * Creates a SpanBuilder instance initialised with attributes from the {@code originalSpan} argument, which then
     * allows the attributes to be overwritten.
     * This method is useful when Spans need to be handled manually, typically for custom framework instrumentation.
     * <p>
     * The returned builder can be built immediately without having to set any properties.
     *
     * @param originalSpan to copy attributes from
     * @return a new Span as a copy of the originalSpan.
     */
    public SpanBuilder createBuilderFrom(final Span originalSpan) {
        if (originalSpan.isNoop()) {
            return noopSpanBuilder;
        }

        final SpanBuilder builder = new SpanBuilder(processor, clock, idProvider, globalSampler);
        builder.getFields().putAll(originalSpan.getFields());
        return builder
            .setTimes(originalSpan.getTimestamp(), originalSpan.getStartTime())
            .setSpanName(originalSpan.getSpanName())
            .setSpanId(originalSpan.getSpanId())
            .setServiceName(originalSpan.getServiceName())
            .setParentContext(new PropagationContext(
                originalSpan.getTraceId(),
                originalSpan.getParentSpanId(),
                originalSpan.getDataset(),
                originalSpan.getTraceFields()
            ));
    }

    /**
     * Creates a Span builder instance initialised so it forms a child-parent relationship with {@code parentSpan}
     * argument. This method is useful when Spans need to be handled manually, typically for custom framework
     * instrumentation.
     * <p>
     * The returned builder must be configured with at least:
     * <ul>
     * <li>spanName</li>
     * </ul>
     * <p>
     *
     * @param parentSpan to link to the
     * @return a new child Span.
     */
    public SpanBuilder createBuilderFromParent(final Span parentSpan) {
        if (parentSpan.isNoop()) {
            return noopSpanBuilder;
        }

        return new SpanBuilder(processor, clock, idProvider, globalSampler)
            .setServiceName(parentSpan.getServiceName())
            .setParentContext(parentSpan.getTraceContext());
    }

    public String generateId() {
        return idProvider.generateId();
    }

    public SpanPostProcessor getProcessor() {
        return processor;
    }

    public ClockProvider getClock() {
        return clock;
    }

    public TraceSampler<? super String> getSampler() {
        return globalSampler;
    }

    /**
     * Close the SpanPostProcessor. This will essentially close the HoneyClient after sending any pending events.
     */
    public void close() {
        processor.close();
    }

    /**
     * Builder to capture various attributes to initialise a Span with.
     * <p>
     * Some properties are already initialised to defaults, but you may have to provide spanName and serviceName.
     * If traceId or spanId are not configured new random IDs will be generated upon calling {@code build()}.
     * <p>
     * Spans are sampled on {@link #build}, based on the implementation of the configured {@link TraceSampler}.
     *
     * <h1>Thread-safety</h1>
     * Instances of this class are not thread-safe.
     */
    @SuppressWarnings("ClassWithTooManyFields")
    public static class SpanBuilder {
        private final SpanPostProcessor processor;
        private final ClockProvider clock;
        private final TraceIdProvider idProvider;
        private final TraceSampler<? super String> traceSampler;

        private final Map<String, Object> fields = new HashMap<>(16);
        @SuppressWarnings("InstanceVariableMayNotBeInitialized")
        private String spanName;
        @SuppressWarnings("InstanceVariableMayNotBeInitialized")
        private String serviceName;
        @SuppressWarnings("InstanceVariableMayNotBeInitialized")
        private Long timestamp;
        @SuppressWarnings("InstanceVariableMayNotBeInitialized")
        private Long startTime;
        @SuppressWarnings("InstanceVariableMayNotBeInitialized")
        private String spanId;
        private PropagationContext parentContext = PropagationContext.emptyContext();

        public SpanBuilder(final SpanPostProcessor processor,
                           final ClockProvider clock,
                           final TraceIdProvider idProvider,
                           final TraceSampler<? super String> traceSampler) {
            this.processor = processor;
            this.clock = clock;
            this.idProvider = idProvider;
            this.traceSampler = traceSampler;
        }

        public SpanBuilder setSpanName(final String spanName) {
            this.spanName = spanName;
            return this;
        }

        public SpanBuilder setServiceName(final String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public SpanBuilder setParentContext(final PropagationContext parentContext) {
            this.parentContext = parentContext;
            return this;
        }

        public SpanBuilder addField(final String key, final Object value) {
            fields.put(key, value);
            return this;
        }

        public SpanBuilder addFields(final Map<String, ?> fieldsToAdd) {
            fields.putAll(fieldsToAdd);
            return this;
        }

        public SpanBuilder setTimes(final long newTimestamp, final long newStartTime) {
            this.timestamp = newTimestamp;
            this.startTime = newStartTime;
            return this;
        }

        public SpanBuilder setSpanId(final String spanId) {
            this.spanId = spanId;
            return this;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getSpanName() {
            return spanName;
        }

        @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType") // we shall leave the builder fully mutable
        public Map<String, Object> getFields() {
            return fields;
        }

        public PropagationContext getParentContext() {
            return parentContext;
        }

        public SpanPostProcessor getProcessor() {
            return processor;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public Long getStartTime() {
            return startTime;
        }

        public String getSpanId() {
            return spanId;
        }

        public ClockProvider getClock() {
            return clock;
        }

        /**
         * Spans returned by this method are subject to sampling based on their {@code traceId}. If the global sampler
         * decides the trace is not to be sampled then it will return a "noop" Span (see {@link Span#isNoop()}.
         * <p>
         * If the {@link SpanPostProcessor} has been configured with a "sampling hook", then for all Spans on a sampled
         * trace, another sampling decision is taken when they are {@linkplain Span#close closed}. In other words,
         * the Spans are passed to the sampling hook just before they are about to be sent.
         *
         * @return an instance of Span with the configured attributes.
         * @throws IllegalArgumentException if required attributes are not set.
         */
        public Span build() {
            final String traceId = parentContext.isTraced() ? parentContext.getTraceId() : idProvider.generateId();
            final int sampleRate = traceSampler.sample(traceId);
            LOG.debug("Building span - sampling decision for traceId '{}' is '{}'", traceId, sampleRate);
            return sampleRate > 0 ? createSendingSpan(sampleRate, traceId) : Span.getNoopInstance();
        }

        private Span createSendingSpan(final int sampleRate, final String traceId) {
            final PropagationContext context = new PropagationContext(
                traceId, parentContext.getSpanId(), parentContext.getDataset(), parentContext.getTraceFields()
            );
            final Span span = new SendingSpan(
                spanName,
                serviceName,
                spanId == null ? idProvider.generateId() : spanId,
                fields,
                context,
                processor,
                clock,
                sampleRate);
            if (timestamp != null) {
                span.markStart(timestamp, startTime);
            }
            return span;
        }
    }

    public static class NoopSpanBuilder extends SpanBuilder {
        public NoopSpanBuilder(
            final SpanPostProcessor processor,
            final ClockProvider clock,
            final TraceIdProvider idProvider,
            final TraceSampler<? super String> traceSampler) {
            super(processor, clock, idProvider, traceSampler);
        }

        @Override
        public Span build() {
            LOG.trace("Building span - noop");
            return Span.getNoopInstance();
        }
    }
}
