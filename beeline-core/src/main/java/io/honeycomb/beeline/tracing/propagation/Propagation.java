package io.honeycomb.beeline.tracing.propagation;

import java.util.Map;

/**
 * Contains helpers for propagation of trace data.
 * <p>
 * For example, decode trace data received over the wire and start a trace:
 *
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
     * Codec that can decode/encode trace context based on Version 1 of the Honeycomb http header ('x-honeycomb-trace').
     *
     * @return a codec.
     */
    public static PropagationCodec<Map<String,String>> honeycombHeaderV1() {
        return HttpHeaderV1PropagationCodec.getInstance();
    }

    /**
     * Codec that can decode/encode trace context based on the AWS http header ('X-Amzn-Trace-Id').
     *
     * @return a codec.
     */
    public static PropagationCodec<Map<String,String>> aws() {
        return AWSPropagationCodec.getInstance();
    }

    /**
     * Codec that can decode/encode trace context based on Version 1 of the W3C http header ('traceparent').
     *
     * @return a codec.
     */
    public static PropagationCodec<Map<String,String>> w3c() {
        return HttpHeaderV1PropagationCodec.getInstance();
    }
}
