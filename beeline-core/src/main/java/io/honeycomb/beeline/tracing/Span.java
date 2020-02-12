package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.libhoney.Event;
import io.honeycomb.libhoney.transport.batch.ClockProvider;
import io.honeycomb.libhoney.transport.batch.impl.SystemClockProvider;
import io.honeycomb.libhoney.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A Span represents an operation over its duration and associates the attributes contained within this class with it.
 * Subclasses of Span transparently implement the mechanism by which a Span is "closed" and submitted to Honeycomb.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are not thread-safe.
 */
@SuppressWarnings({"ClassWithTooManyFields", "ClassWithTooManyMethods"})
public class Span implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Span.class);

    /** Using HashMap's default initial capacity, since we don't expect it to be used much. */
    private static final int TRACE_FIELDS_CAPACITY = 16;
    /**
     * Using a relatively high initial capacity for fields. For instance, for server spans we expect over 30 entries.
     * We add some extra to avoid resizing, which happens at capacity * load factor (normally 0.75).
     */
    private static final int FIELDS_CAPACITY = 70;
    private static final double NANOS_TO_MILLIS_DIVISOR = 1_000_000.0;
    private static final String NOOP_STRING = "NOOP";

    /**
     * If true then this Span is a noop, meaning it's a Span that does nothing (doesn't carry data,
     * doesn't send to honeycomb). At the same time it allows interfaces that return Span to remain null-safe.
     * <p>
     * Within the Beeline library code method invocations and usages of a "noop" Span should avoid unnecessary
     * computations and allocations (e.g. by implementing short-circuiting logic). Normal user code should avoid making
     * any distinctions between "noop" and normal Spans.
     * <p>
     * {@link #getNoopInstance()} returns a default implementation for such a span.
     */
    private final boolean noop;
    /**
     * Gives a name to the operation covered by the Span. Typically, the name should be relatively low cardinality,
     * so that it identifies the operation in general, rather than any specific invocations of it. For example,
     * the name of the method the Span represents.
     * <p>
     * - Must not be empty.
     */
    private final String spanName;
    /**
     * Name given to this service/application - must not be empty.
     */
    private final String serviceName;
    /**
     * An ID that identifies the parent of this Span, if it has one - may be null.
     */
    private final String parentSpanId;
    /**
     * An ID to identify the trace as a whole - must not be empty.
     */
    private final String traceId;
    /**
     * An ID that identifies the Span - must not be empty & must be unique within the trace.
     */
    private final String spanId;
    /**
     * Acts as an override over the Beeline's configured dataset where the Span is sent to - may be null.
     */
    private final String dataset;
    /**
     * A Map that contains information that propagates down the trace.
     */
    private final Map<String, Object> traceFields;
    /**
     * A Map that contains that contain information specific to the Span.
     */
    private final Map<String, Object> fields;
    /**
     * The clock provides timestamps and measurements for this Span.
     */
    private final ClockProvider clock;

    private static final long ABSENT_TIME = -1L;
    private long startTimestamp = ABSENT_TIME;
    private long startOfElapsedTime = ABSENT_TIME;

    /**
     * Flag to indicate whether close has already been called.
     */
    private boolean closed;

    /**
     * Constructor that initialises the base Span data.
     * It also calls {@link #markStart} immediately, which may be reset by client code by calling it any other point.
     *
     * @param spanName    must not be empty.
     * @param serviceName must not be empty.
     * @param spanId      must not be empty.
     * @param context     containing data about the trace - must not be null, and it must contain a traceId.
     * @param fields      may be null or empty.
     * @param clock       must not be null.
     */
    @SuppressWarnings("ConstructorWithTooManyParameters")
    public Span(final String spanName,
                final String serviceName,
                final String spanId,
                final PropagationContext context,
                final Map<String, ?> fields,
                final ClockProvider clock) {
        Assert.notEmpty(spanName, "Validation failed: spanName is required");
        Assert.notEmpty(serviceName, "Validation failed: serviceName is required");
        Assert.notEmpty(spanId, "Validation failed: spanId is required");
        Assert.notEmpty(context.getTraceId(), "Validation failed: context traceId is required");
        Assert.notNull(clock, "Validation failed: clock is required");

        this.spanName = spanName;
        this.serviceName = serviceName;
        this.spanId = spanId;
        this.parentSpanId = context.getSpanId();
        this.traceId = context.getTraceId();
        this.traceFields = context.getTraceFields() == null ?
            new HashMap<>(TRACE_FIELDS_CAPACITY) :
            new HashMap<>(context.getTraceFields());
        this.dataset = context.getDataset();
        this.fields = fields == null ? new HashMap<>(FIELDS_CAPACITY) : new HashMap<>(fields);
        this.clock = clock;
        this.noop = false;
        startTimers();
    }

    /**
     * Constructor that initialises this Span with "noop" data and sets the noop flag to true.
     *
     * @see #noop
     */
    protected Span() {
        this.spanName = NOOP_STRING;
        this.serviceName = NOOP_STRING;
        this.parentSpanId = NOOP_STRING;
        this.traceId = NOOP_STRING;
        this.spanId = NOOP_STRING;
        this.traceFields = Collections.emptyMap();
        this.fields = Collections.emptyMap();
        this.clock = SystemClockProvider.getInstance();
        this.noop = true;
        this.dataset = NOOP_STRING;
    }

    private void startTimers() {
        this.startTimestamp = clock.getWallTime();
        this.startOfElapsedTime = clock.getMonotonicTime();
    }

    @SuppressWarnings("resource")
    private static final Span INSTANCE = new Span();

    public static Span getNoopInstance() {
        return INSTANCE;
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * Add a field to the Span.
     *
     * @param key   of the field.
     * @param value of the field.
     * @return this span.
     */
    public Span addField(final String key, final Object value) {
        if (isNoop()) return this;

        this.fields.put(key, value);
        return this;
    }

    /**
     * Add a fields to the Span.
     *
     * @param fieldsToAdd to add.
     * @return this Span.
     */
    public Span addFields(final Map<String, ?> fieldsToAdd) {
        if (isNoop()) return this;

        this.fields.putAll(fieldsToAdd);
        return this;
    }

    /**
     * Add a trace field to the Span.
     *
     * @param key   of the field.
     * @param value of the field.
     * @return this Span.
     */
    public Span addTraceField(final String key, final Object value) {
        if (isNoop()) return this;

        this.traceFields.put(key, value);
        return this;
    }

    /**
     * Add trace fields to the Span.
     *
     * @param traceFieldsToAdd to add.
     * @return this Span.
     */
    public Span addTraceFields(final Map<String, ?> traceFieldsToAdd) {
        if (isNoop()) return this;

        this.traceFields.putAll(traceFieldsToAdd);
        return this;
    }

    /**
     * Reset the Span's timer.
     * <p>
     * The Span always starts the timer upon construction of the instance. However, if the operation covered by the
     * Span starts later you can reset its timer with this (e.g. for async use cases).
     *
     * @return this Span.
     */
    public Span markStart() {
        if (isNoop()) return this;

        this.startTimestamp = clock.getWallTime();
        this.startOfElapsedTime = clock.getMonotonicTime();
        return this;
    }

    /**
     * Sets the times with the specified values - useful when copying Spans.
     *
     * @param timestamp (millis since the epoch)
     * @param startTime start time (monotonic JVM time)
     * @return this Span.
     */
    public Span markStart(final long timestamp, final long startTime) {
        if (isNoop()) return this;

        this.startTimestamp = timestamp;
        this.startOfElapsedTime = startTime;
        return this;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getDataset() {
        return dataset;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getSpanName() {
        return spanName;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * @return an unmodifiable reference to the Span's fields.
     * @see #fields
     */
    public Map<String, Object> getFields() {
        if (isNoop()) return Collections.emptyMap();

        return Collections.unmodifiableMap(fields);
    }

    /**
     * @return an unmodifiable reference to the Span's trace fields.
     * @see #traceFields
     */
    public Map<String, Object> getTraceFields() {
        if (isNoop()) return Collections.emptyMap();

        return Collections.unmodifiableMap(traceFields);
    }

    /**
     * @return a PropagationContext with the Span's IDs and trace fields - useful for propagating traces.
     */
    public PropagationContext getTraceContext() {
        if (isNoop()) return PropagationContext.emptyContext();

        return new PropagationContext(traceId, spanId, dataset, getTraceFields());
    }

    /**
     * @return the time elapsed since "markStart" as a double with sub-millis in the fractional part.
     * @see #markStart()
     */
    public double elapsedTimeMs() {
        if (isNoop()) return 0.0;

        return (double) (clock.getMonotonicTime() - startOfElapsedTime) / NANOS_TO_MILLIS_DIVISOR;
    }

    /**
     * @return the timestamp (millis since the epoch) of when "markStart" was called.
     * @see #markStart()
     */
    public long getTimestamp() {
        return startTimestamp;
    }

    /**
     * @return the start time (monotonic JVM time).
     * @see #markStart()
     */
    public long getStartTime() {
        return startOfElapsedTime;
    }

    /**
     * Closes this span in order to submit it as an {@link Event} to Honeycomb and perform any necessary clean up in
     * the process. Overrides of {@link Span#closeInternal()} contain the logic to do this.
     * <p>
     * The Span's duration is measured upon calling this method (as per {@link #elapsedTimeMs}).
     * <p>
     * In order to avoid clashes, because of multiple events with the same {@code span_id} being submitted, this method
     * ensures that it is only called once and ignores multiple invocations. However, it does not use any
     * synchronization to ensure this and is therefore not thread safe.
     * <p>
     * This uses method allows {@code Span} to be used with try-with-resources statements.
     */
    @Override
    public void close() {
        if (isNoop()) {
            return;
        }

        if (closed) {
            LOG.debug(
                "Span has already been closed. Ignoring call - traceId: '{}', spanName: '{}', spanId: '{}'",
                getTraceId(), getSpanName(), getSpanId());
        } else {
            closed = true;
            closeInternal();
        }
    }

    /**
     * Subclasses should override this to implement the mechanism
     */
    protected void closeInternal() {

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
               "noop=" + isNoop() +
               ", spanName='" + getSpanName() + '\'' +
               ", serviceName='" + getServiceName() + '\'' +
               ", parentSpanId='" + getParentSpanId() + '\'' +
               ", traceId='" + getTraceId() + '\'' +
               ", spanId='" + getSpanId() + '\'' +
               ", traceFields=" + getTraceFields() +
               ", fields=" + getFields() +
               ", clock=" + clock +
               ", startTimestamp=" + getStartTime() +
               ", startOfElapsedTime=" + startOfElapsedTime +
               ", closed=" + closed +
               '}';
    }
}
