package io.honeycomb.beeline.tracing.ids;

/**
 * Interface that produces IDs for traces and spans.
 *
 * <h1>Thread-safety</h1>
 * Implementations must be thread-safe and so that they can be shared.
 */
public interface TraceIdProvider {
    @Deprecated
    String generateId();

    String generateTraceId();
    String generateSpanId();
}
