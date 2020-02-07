package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.ids.TraceIdProvider;
import io.honeycomb.beeline.tracing.ids.UUIDTraceIdProvider;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.beeline.tracing.context.TracingContext;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import io.honeycomb.libhoney.transport.batch.impl.SystemClockProvider;

/**
 * This class represents an entry point to Honeycomb's core tracing functionality with factory methods for
 * {@link Beeline} (make sure to read its class Javadoc first), {@link SpanBuilderFactory}, and {@link Tracer}.
 * <p>
 * SpanBuilderFactory helps with the construction of Span instances, while Tracer provides an API to manage Spans
 * within its thread-local context. The Beeline class is a convenient façade to both.
 * <p>
 * For more details, see the Javadoc of each class.
 */
public final class Tracing {
    private static final ClockProvider CLOCK = SystemClockProvider.getInstance();
    private static final TraceIdProvider ID_PROVIDER = UUIDTraceIdProvider.getInstance();

    private Tracing() {
        // utils class
    }

    /**
     * Creates a {@link Beeline} serves as the main point of interaction with traces in a Beeline-instrumented
     * application.
     *
     * @param tracer  to use to manage the Spans within its thread-local context.
     * @param factory used to create Builders of Spans.
     * @return an instance of Beeline.
     */
    public static Beeline createBeeline(final Tracer tracer, final SpanBuilderFactory factory) {
        return new Beeline(tracer, factory);
    }

    /**
     * Factory method to create a new {@link Tracer} instance,
     * which helps manage tracing instrumentation and reports spans to Honeycomb using the provided client.
     * See the Tracer's javadoc for details.
     *
     * @param factory used to create Builders of Spans.
     * @return an instance of Tracer.
     * @see #createSpanBuilderFactory(SpanPostProcessor, TraceSampler)
     */
    public static Tracer createTracer(final SpanBuilderFactory factory) {
        return new Tracer(factory);
    }

    /**
     * Factory method to create a new {@link Tracer} instance,
     * which helps manage tracing instrumentation and reports spans to Honeycomb using the provided client.
     * See the Tracer's javadoc for details.
     *
     * @param factory used to create Builders of Spans.
     * @return an instance of Tracer.
     * @see #createSpanBuilderFactory(SpanPostProcessor, TraceSampler)
     */
    public static Tracer createTracer(final SpanBuilderFactory factory, TracingContext context ) {
        return new Tracer(factory, context);
    }

    /**
     * Creates a {@link SpanBuilderFactory} that helps with the construction of Span instances and configures them with
     * the provided client and sampler, as well as default implementation of
     * {@link ClockProvider} and {@link TraceIdProvider}.
     *
     * @param processor     to use to send spans.
     * @param globalSampler to use when deciding whether a constructed Span will be sampled.
     * @return an instance of SpanBuilderFactory.
     */
    public static SpanBuilderFactory createSpanBuilderFactory(final SpanPostProcessor processor,
                                                              final TraceSampler<? super String> globalSampler) {
        return new SpanBuilderFactory(processor, CLOCK, ID_PROVIDER, globalSampler);
    }

    /**
     * Creates a {@link SpanPostProcessor} that performs post-processing of Spans. It can apply additional sampling
     * based on the contents of a Span, and it converts {@link Span} instances into {@link io.honeycomb.libhoney.Event}
     * that can be sent to Honeycomb.
     *
     * @param client       to use to send events that it generates.
     * @param samplingHook to use when deciding whether to sample a Span.
     * @return an instance of SpanBuilderFactory.
     */
    public static SpanPostProcessor createSpanProcessor(final HoneyClient client,
                                                        final TraceSampler<? super Span> samplingHook) {
        return new SpanPostProcessor(client, samplingHook);
    }
}
