package io.honeycomb.beeline.tracing.propagation;

import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.honeycomb.beeline.tracing.ids.W3CTraceIdProvider;

import static io.honeycomb.libhoney.utils.ObjectUtils.isNullOrEmpty;

/**
 * Codec that can decode/encode trace context based on the W3C http header ('traceparent').
 * Prefer the {@link Propagation#w3c()} factory method to retrieve an instance.
 * <p>
 * This implementation does not support the 'tracecontext' http header so no tracefields can be extracted or
 * encoded.
 * <p>
 * The design of this class avoids throwing exceptions in favour of logging warnings and returning null on encode
 * or an "empty context" on decode.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared.
 */
public class W3CPropagationCodec implements PropagationCodec<String> {

    private static final Logger LOG = LoggerFactory.getLogger(W3CPropagationCodec.class);
    private static final W3CPropagationCodec INSTANCE = new W3CPropagationCodec();

    // @formatter:off
    public static final String W3C_TRACEPARENT_HEADER       = "traceparent";

    private static final String DEFAULT_VERSION             = "00";
    private static final String NOT_SAMPLED_TRACEFLAGS      = "00";
    private static final String SAMPLED_TRACEFLAGS          = "01";
    private static final String SEGMENT_SEPARATOR           = "-";
    private static final Pattern SPLIT_SEGMENTS_PATTERN     = Pattern.compile(SEGMENT_SEPARATOR);

    private static final int HEADER_LENGTH                  = 55; // {version:2}-{trace-id:32}-{parent-id:16}-{traceflags:2}
    // @formatter:on

    public static W3CPropagationCodec getInstance() {
        return INSTANCE;
    }

    /**
     * This decodes the 'traceparent' header of the following format:
     * <p>
     * {@code {version}-{traceId}-{spanId}-{traceflags}}
     * <p>
     *
     * </p>
     *
     * @param encodedTrace to decode into a {@link PropagationContext}.
     * @return extracted context - "empty" context if encodedTrace value has an invalid format or is null.
     */
    @Override
    public PropagationContext decode(String encodedTrace) {
        // Header must not be empty and be 55 characters long
        if (isNullOrEmpty(encodedTrace) || encodedTrace.length() != HEADER_LENGTH) {
            return PropagationContext.emptyContext();
        }

        // Header should have four parts
        final String[] segments = SPLIT_SEGMENTS_PATTERN.split(encodedTrace);
        if (segments.length != 4) {
            LOG.warn("Invalid W3C trace header: {}", encodedTrace);
            return PropagationContext.emptyContext();
        }

        // Only allow version '00'
        if (!segments[0].equals(DEFAULT_VERSION)) {
            LOG.warn("Invalid header version header: {}", encodedTrace);
            return PropagationContext.emptyContext();
        }

        // Check TraceId length is 32 characters and it is valid (none zero)
        if (!W3CTraceIdProvider.isValidTraceId(segments[1])) {
            LOG.warn("Invalid TraceId: {}", segments[1]);
            return PropagationContext.emptyContext();
        }

        // Check SpanId length is 16 characters and it is valid (none zero)
        if (!W3CTraceIdProvider.isValidSpanId(segments[2])) {
            LOG.warn("Invalid SpanId: {}", segments[2]);
            return PropagationContext.emptyContext();
        }

        // Check TraceFlags is valid
        if (!segments[3].equals(SAMPLED_TRACEFLAGS) && !segments[3].equals(NOT_SAMPLED_TRACEFLAGS)) {
            LOG.warn("Invalid TraceFlags: {}", segments[3]);
            return PropagationContext.emptyContext();
        }

        return new PropagationContext(segments[1], segments[2], null, null);
    }

    /**
     * This encodes the given propagation context in the format accepted by the W3C 'traceparent' http header.
     * This will return null if the span id or trace id are not set (i.e. the context is not an active trace).
     *
     * @param context to encode into a valid header value.
     * @return a valid W3C traceparent header value - empty if required IDs are missing or input is null.
     */
    @Override
    public Optional<String> encode(PropagationContext context) {
        // Check context is valid
        if (context == null) {
            return Optional.empty();
        }

        if (!W3CTraceIdProvider.isValidTraceId(context.getTraceId())) {
            LOG.warn("Unable to encode TraceId to W3C format: {}", context.getTraceId());
            return Optional.empty();
        }

        if (!W3CTraceIdProvider.isValidSpanId(context.getSpanId())) {
            LOG.warn("Unable to encode SpanId to W3C format: {}", context.getSpanId());
            return Optional.empty();
        }

        final StringBuilder builder = new StringBuilder(HEADER_LENGTH)
            .append(DEFAULT_VERSION).append(SEGMENT_SEPARATOR)
            .append(context.getTraceId()).append(SEGMENT_SEPARATOR)
            .append(context.getSpanId()).append(SEGMENT_SEPARATOR)
            .append(NOT_SAMPLED_TRACEFLAGS);

        return Optional.of(builder.toString());
    }
}
