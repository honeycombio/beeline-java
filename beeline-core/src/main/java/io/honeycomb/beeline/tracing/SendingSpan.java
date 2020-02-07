package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.libhoney.Event;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import io.honeycomb.libhoney.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Implements close with the ability to send Spans to Honeycomb using the Event returned by the configured
 * {@link SpanPostProcessor}, which adds the standard fields that are required for Honeycomb to recognise an Event as a
 * Span.
 * <p>
 * Close must only be called once, and subsequent invocations will be ignored.
 * <p>
 * If you need to construct Spans of this type, you can make use {@link SpanBuilderFactory}.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are not thread-safe.
 */
public class SendingSpan extends Span {
    private static final Logger LOG = LoggerFactory.getLogger(SendingSpan.class);

    private final SpanPostProcessor processor;
    /**
     * Refers to rate given by the sampling done before constructing this instance.
     */
    private final int initialSampleRate;

    @SuppressWarnings("ConstructorWithTooManyParameters") // people should use SpanBuilderFactory to build this
    public SendingSpan(final String spanName,
                       final String serviceName,
                       final String spanId,
                       final Map<String, ?> fields,
                       final PropagationContext context,
                       final SpanPostProcessor processor,
                       final ClockProvider clock,
                       final int initialSampleRate) {
        super(spanName, serviceName, spanId, context, fields, clock);
        Assert.notNull(processor, "Validation failed: processor is required");
        this.initialSampleRate = initialSampleRate;
        this.processor = processor;
    }

    /**
     * Closes the span by generating an Event using the configured {@link SpanPostProcessor}, adding required fields,
     * applying the sampling hook, and, if sampled, sends it to Honeycomb.
     * <p>
     * The event's sampling rate is set to the product of the {@link #initialSampleRate} and the sampling rate returned
     * by {@link SpanPostProcessor#runSamplerHook(Span)}.
     */
    @Override
    protected void closeInternal() {
        LOG.debug(
            "Sending Span to Honeycomb - traceId: '{}', spanName: '{}', spanId: '{}'",
            getTraceId(), getSpanName(), getSpanId());

        final int samplerHookRate = processor.runSamplerHook(this);
        final int finalSamplingRate = initialSampleRate * samplerHookRate;
        if (finalSamplingRate <= 0) {
            return;
        }
        final Event event = processor.generateEvent(this);
        event.setSampleRate(finalSamplingRate);
        event.sendPresampled();
    }

    /**
     * Returns the initial sample rate.
     *
     * @return the sampler rate set for this span.
     */
    public int getInitialSampleRate() {
        return initialSampleRate;
    }

    /**
     * Getter for the processor, in case future development needs access to it.
     *
     * @return this spans processor.
     */
    protected SpanPostProcessor getProcessor() {
        return processor;
    }

}
