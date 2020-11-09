package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.libhoney.utils.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.honeycomb.libhoney.utils.ObjectUtils.isNullOrEmpty;

/**
 * This class represents the information that is needed to continue traces across process boundaries:
 * <ol>
 * <li>The <b>traceId</b> that identifies the trace as a whole.</li>
 * <li>The <b>spanId</b> that identifies the latest Span in the trace and
 * will become the <b>parentSpanId</b> of the next Span in the trace.</li>
 * <li><em>Optional</em> - <b>Trace fields</b> that propagate contextual information down the trace.</li>
 * <li><em>Optional</em> - The name of the dataset to send the Spans to.
 * This acts as an override for the application's configured dataset.</li>
 * </ol>
 * <p>Thread-safety</p>
 * Instances of this class are thread-safe.
 */
public class PropagationContext {
    private static final PropagationContext EMPTY_CONTEXT = new PropagationContext(null, null, null, null);

    private final String spanId;
    private final String traceId;
    private final String dataset;
    private final Map<String, Object> traceFields;

    /**
     * Create a trace context with provided IDs and map of trace fields.
     *
     * @param traceId     that identifies the trace - may be null.
     * @param spanId      that identifies the latest span - may only be null if span ID is also null.
     * @param dataset     that identifies an explicit dataset that spans should be sent to - may be null.
     * @param traceFields to propagate to the next span - may be null.
     * @see Span#getTraceContext()
     */
    public PropagationContext(final String traceId,
                              final String spanId,
                              final String dataset,
                              final Map<String, ?> traceFields) {
        Assert.isTrue(
            isNullOrEmpty(spanId) || !isNullOrEmpty(traceId),
            "Context must also be initialised with a traceId if passed a spanId");
        this.traceFields = traceFields == null ?
            Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(traceFields));
        this.spanId = spanId;
        this.traceId = traceId;
        this.dataset = dataset;
    }

    public static PropagationContext emptyContext() {
        return EMPTY_CONTEXT;
    }

    /**
     * @return immutable map of fields being propagated across the trace - may be empty.
     */
    public Map<String, Object> getTraceFields() {
        return traceFields;
    }

    /**
     * @return Trace ID - may be null.
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * @return Span ID - may be null.
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * @return dataset - may be null.
     */
    public String getDataset() {
        return dataset;
    }

    /**
     * @return true if this context has trace and span IDs.
     */
    public boolean isTraced() {
        return !isNullOrEmpty(traceId);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final PropagationContext that = (PropagationContext) other;
        return Objects.equals(spanId, that.spanId) &&
               Objects.equals(traceId, that.traceId) &&
               Objects.equals(dataset, that.dataset) &&
               Objects.equals(traceFields, that.traceFields);
    }

    @Override
    public int hashCode() {
        //noinspection ObjectInstantiationInEqualsHashCode
        return Objects.hash(spanId, traceId, dataset, traceFields);
    }

    @Override
    public String toString() {
        return "PropagationContext{" +
               "spanId='" + spanId + '\'' +
               ", traceId='" + traceId + '\'' +
               ", dataset='" + dataset + '\'' +
               ", traceFields=" + traceFields +
               '}';
    }
}
