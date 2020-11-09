package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.Event;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.utils.Assert;

/**
 * This applies post processing to Spans that are ready to be sent. Namely, it can apply the {@code samplerHook} and
 * convert {@linkplain Span Spans} into {@link Event Events} that can be submitted to Honeycomb.
 *
 * <p>Thread-safety</p>
 * Instances of this class are thread-safe and can be shared.
 */
public class SpanPostProcessor {
    private final HoneyClient client;
    private final TraceSampler<? super Span> samplerHook;

    public SpanPostProcessor(final HoneyClient client, final TraceSampler<? super Span> samplerHook) {
        Assert.notNull(client, "Validation failed: client is required");
        Assert.notNull(samplerHook, "Validation failed: samplerHook is required");

        this.client = client;
        this.samplerHook = samplerHook;
    }

    /**
     * Applies the sampler hook to the contents of the Span and decides whether to sample it.
     * If the return value is 0, then it should not be sampled.
     * If positive then it should be sampled, and the concrete value represents the sample rate.
     *
     * @param span to pass to the sampler hook.
     * @return sampling result.
     */
    public int runSamplerHook(final Span span) {
        return samplerHook.sample(span);
    }

    /**
     * Generates an Event with required tracing fields that are required for Honeycomb to recognise an Event as a Span.
     *
     * @param span to convert to an Event.
     * @return the generated Event.
     */
    public Event generateEvent(final Span span) {
        final Event event = client.createEvent();
        event.addFields(span.getTraceFields());
        event.addFields(span.getFields());
        applySpanToEvent(span, event);
        event
            .setTimestamp(span.getTimestamp())
            .addField(TraceFieldConstants.DURATION_FIELD, span.elapsedTimeMs());
        return event;
    }

    private void applySpanToEvent(final Span span, final Event event) {
        if (span.getParentSpanId() != null) {
            event.addField(TraceFieldConstants.PARENT_ID_FIELD, span.getParentSpanId());
        }
        if (span.getDataset() != null) {
            event.setDataset(span.getDataset());
        }
        event
            .addField(TraceFieldConstants.SPAN_NAME_FIELD, span.getSpanName())
            .addField(TraceFieldConstants.SERVICE_NAME_FIELD, span.getServiceName())
            .addField(TraceFieldConstants.SPAN_ID_FIELD, span.getSpanId())
            .addField(TraceFieldConstants.TRACE_ID_FIELD, span.getTraceId());
    }

    /**
     * Close the HoneyClient instance. This will send any pending events.
     */
    public void close() {
        client.close();
    }


    /**
     * Generates an Event with required fields to be recognized as an Event as a Span Event. Sleuth annotations will be
     * converted into Span Events.
     *
     * @param span covert this span to a Span Event
     * @return the generated Span Event
     * @see <a href="http://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#span-events">Span Events</a>
     */
    public Event generateSpanEvent(final Span span) {
        final Event event = client.createEvent();
        event.addField(TraceFieldConstants.META_SPAN_TYPE_FIELD, TraceFieldConstants.META_TYPE_SPAN_EVENT_VALUE);
        applySpanToEvent(span, event);
        return event;
    }
}
