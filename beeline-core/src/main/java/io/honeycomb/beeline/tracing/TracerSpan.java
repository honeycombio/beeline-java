package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.utils.ThreadIdentifierObject;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This class represents a Span that is managed by a {@link Tracer}.
 * <p>
 * It overrides all methods of Span, and then simply delegates method invocations to the Span instance that it wraps.
 * <p>
 * The only added functionality is in {@link #close()}, which is used to notify the Tracer of the TracerSpan being
 * closed prior to invoking close on the delegate Span. This ensures that the TracerSpan can detach itself from the
 * Tracer context and perform any necessary cleanup.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are not thread-safe. See the notes on the {@link Tracer}'s javadoc for details.
 */
@SuppressWarnings("ClassWithTooManyMethods") // We override all methods of Span, thus the method count is high.
public class TracerSpan extends Span {
    private static final Logger LOG = LoggerFactory.getLogger(TracerSpan.class);
    @SuppressWarnings("resource")
    private static final TracerSpan NOOP_TRACER_SPAN = new TracerSpan(Span.getNoopInstance(), null);

    private final ThreadIdentifierObject originalId;
    private final Span delegate;
    private final Tracer tracer;

    TracerSpan(final Span span, final Tracer tracer) {
        this.delegate = span;
        this.tracer = tracer;
        this.originalId = ThreadIdentifierObject.getCurrentThreadId();
    }

    static TracerSpan getNoopTracerSpan() {
        return NOOP_TRACER_SPAN;
    }

    Span getDelegate() {
        return delegate;
    }

    boolean isFromCurrentTrace() {
        return getTraceId().equals(tracer.getActiveSpan().getTraceId());
    }

    @Override
    public Span addField(final String key, final Object value) {
        return delegate.addField(key, value);
    }

    @Override
    public Span addFields(final Map<String, ?> fieldsToAdd) {
        return delegate.addFields(fieldsToAdd);
    }

    @Override
    public Span addTraceField(final String key, final Object value) {
        return delegate.addTraceField(key, value);
    }

    @Override
    public Span addTraceFields(final Map<String, ?> traceFieldsToAdd) {
        return delegate.addTraceFields(traceFieldsToAdd);
    }

    @Override
    public Span markStart() {
        return delegate.markStart();
    }

    @Override
    public Span markStart(final long timestamp, final long startTime) {
        return delegate.markStart(timestamp, startTime);
    }

    @Override
    public String getParentSpanId() {
        return delegate.getParentSpanId();
    }

    @Override
    public String getTraceId() {
        return delegate.getTraceId();
    }

    @Override
    public String getDataset() {
        return delegate.getDataset();
    }

    @Override
    public String getSpanId() {
        return delegate.getSpanId();
    }

    @Override
    public String getSpanName() {
        return delegate.getSpanName();
    }

    @Override
    public String getServiceName() {
        return delegate.getServiceName();
    }

    @Override
    public Map<String, Object> getFields() {
        return delegate.getFields();
    }

    @Override
    public Map<String, Object> getTraceFields() {
        return delegate.getTraceFields();
    }

    @Override
    public PropagationContext getTraceContext() {
        return delegate.getTraceContext();
    }

    @Override
    public double elapsedTimeMs() {
        return delegate.elapsedTimeMs();
    }

    @Override
    public long getTimestamp() {
        return delegate.getTimestamp();
    }

    @Override
    public long getStartTime() {
        return delegate.getStartTime();
    }

    @Override
    public boolean isNoop() {
        return delegate.isNoop();
    }

    @Override
    public void close() {
        if (isNoop()) {
            return;
        }

        if (ThreadIdentifierObject.isFromCurrentThread(originalId)) {
            tracer.popSpan(this);
        } else {
            delegate.addField(TraceFieldConstants.META_DIRTY_CONTEXT_FIELD, true);
            LOG.warn("Tracer span being closed on a different thread or trace to where it was created, " +
                     "indicating misuse of the tracer API.");
        }
        super.close();
    }

    @Override
    protected void closeInternal() {
        delegate.close();
    }

    @Override
    public String toString()
    {
        return "TracerSpan{" + "delegate=" + delegate + '}';
    }
}

