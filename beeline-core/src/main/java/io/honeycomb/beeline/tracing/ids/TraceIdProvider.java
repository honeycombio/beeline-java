package io.honeycomb.beeline.tracing.ids;

/**
 * Interface that produces IDs for traces and spans.
 *
 * <p>Thread-safety</p>
 * Implementations must be thread-safe and so that they can be shared.
 */
public interface TraceIdProvider {
    @Deprecated
    String generateId();

    String generateTraceId();
    String generateSpanId();
}
