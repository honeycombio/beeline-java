package io.honeycomb.beeline.tracing.ids;

import java.util.UUID;

/**
 * Produces a random UUID (v4) string as trace IDs.
 * This is inline with Beeline instrumentations in other programming languages.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared.
 */
public class UUIDTraceIdProvider implements TraceIdProvider {
    private static final TraceIdProvider INSTANCE = new UUIDTraceIdProvider();

    public static TraceIdProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public String generateId() {
        return UUID.randomUUID().toString();
    }
}
