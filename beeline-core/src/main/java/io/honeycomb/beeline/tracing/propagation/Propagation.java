package io.honeycomb.beeline.tracing.propagation;

/**
 * Contains helpers for propagation of trace data.
 * <p>
 * For example, decode trace data received over the wire and start a trace:
 * <pre>
 * PropagationContext context = Propagation.honeycombHeaderV1().decode(traceHeader);
 * tracer.startTrace("customer-db", "get-customer-data", context);
 * </pre>
 */
public final class Propagation {
    private Propagation() {
        // utils class
    }

    /**
     * Codec that can decode/encode trace context based on Version 1 of the honeycomb http header ('x-honeycomb-trace').
     *
     * @return a codec.
     */
    public static PropagationCodec<String> honeycombHeaderV1() {
        return HttpHeaderV1PropagationCodec.getInstance();
    }

    /**
     * Codec that can decode/encode trace context based on the Amazon X-Ray http header ('X-Amzn-Trace-Id').
     *
     * @return a codec.
     */
    public static PropagationCodec<String> amazonXRay() {
        return AmazonXRayPropagationCodec.getInstance();
    }
}
