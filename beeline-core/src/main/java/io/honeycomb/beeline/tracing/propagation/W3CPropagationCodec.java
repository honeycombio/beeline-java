package io.honeycomb.beeline.tracing.propagation;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.honeycomb.beeline.tracing.ids.W3CTraceIdProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
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
 * <p>Thread-safety</p>
 * Instances of this class are thread-safe and can be shared.
 */
public class W3CPropagationCodec implements PropagationCodec<Map<String, String>> {

    private static final Logger LOG = LoggerFactory.getLogger(W3CPropagationCodec.class);
    private static final W3CPropagationCodec INSTANCE = new W3CPropagationCodec();

    // @formatter:off
    protected static final String CODEC_NAME                = "w3c";
    protected static final String W3C_TRACEPARENT_HEADER    = "traceparent";
    protected static final String W3C_TRACESTATE_HEADER     = "tracestate";

    private static final String DEFAULT_VERSION             = "00";
    private static final String NOT_SAMPLED_TRACEFLAGS      = "00";
    private static final String SAMPLED_TRACEFLAGS          = "01";
    private static final String SEGMENT_SEPARATOR           = "-";
    private static final Pattern SPLIT_SEGMENTS_PATTERN     = Pattern.compile(SEGMENT_SEPARATOR);
    private static final int HEADER_LENGTH                  = 55; // {version:2}-{trace-id:32}-{parent-id:16}-{traceflags:2}
    private static final String DATASET_STRING              = "dataset";
    private static final String HONEYCOMB_TRACESTATE_VENDOR = "hny";
    private static final String TRACESTATE_VENDOR_SEPARATOR = ",";
    private static final String TRACESTATE_VALUE_SEPARATOR  = "=";
    private static final String HONEYCOMB_VENDOR_PREFIX     = HONEYCOMB_TRACESTATE_VENDOR + TRACESTATE_VALUE_SEPARATOR;
    // @formatter:on

    public static W3CPropagationCodec getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the codec name.
     */
    public String getName() {
        return CODEC_NAME;
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
    public PropagationContext decode(Map<String, String> headers) {
        if (headers == null || !headers.containsKey(W3C_TRACEPARENT_HEADER)) {
            return PropagationContext.emptyContext();
        }
        final String encodedTrace = headers.getOrDefault(W3C_TRACEPARENT_HEADER, null);
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

        // try to parse dataset and other trace fields from tracestate header
        String dataset = null;
        Map<String, String> fields = null;
        String encodedState = headers.get(W3C_TRACESTATE_HEADER);
        if (encodedState != null) {
            // check if we have a honeycomb value
            int startPos = encodedState.indexOf(HONEYCOMB_VENDOR_PREFIX);
            if (startPos >= 0) {
                // get end position
                int keyEndPos = encodedState.indexOf(TRACESTATE_VENDOR_SEPARATOR, startPos);
                String honeycombState = keyEndPos == -1 ?
                    encodedState.substring(startPos + 4) :
                    encodedState.substring(startPos + 4, keyEndPos);

                if (!honeycombState.isEmpty()) {
                    String decodedState = new String(Base64.getDecoder().decode(honeycombState), UTF_8);
                    for (String kvp : decodedState.split(TRACESTATE_VENDOR_SEPARATOR)) {
                        String[] parts = kvp.split(TRACESTATE_VALUE_SEPARATOR);
                        if (parts[0].equals(DATASET_STRING)) {
                            // don't add dataset
                        } else {
                            if (fields == null) {
                                fields = new HashMap<>();
                            }
                            fields.put(parts[0], parts[1]);
                        }
                    }
                }
            }
        }

        return new PropagationContext(segments[1], segments[2], dataset, fields);
    }

    /**
     * This encodes the given propagation context in the format accepted by the W3C 'traceparent' http header.
     * This will return null if the span id or trace id are not set (i.e. the context is not an active trace).
     *
     * @param context to encode into a valid header value.
     * @return a valid W3C traceparent header value - empty if required IDs are missing or input is null.
     */
    @Override
    public Optional<Map<String, String>> encode(PropagationContext context) {
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

        final String traceParent = String.join(SEGMENT_SEPARATOR, DEFAULT_VERSION, context.getTraceId(), context.getSpanId(), SAMPLED_TRACEFLAGS);

        // If no dataset or tracefields, just return trace parent header
        if (context.getDataset() == null && context.getTraceFields().isEmpty()) {
            return Optional.of(
                Collections.singletonMap(W3C_TRACEPARENT_HEADER, traceParent)
            );
        }

        final boolean[] first = {true}; // trick for allowing scoped variables to be accessed inside lambda functions
        final StringBuilder builder = new StringBuilder();

        // Sort the fields by key and append
        context.getTraceFields().entrySet().stream()
            .sorted(Map.Entry.<String, Object>comparingByKey())
            .forEach(field -> {
                if (!first[0]) {
                    builder.append(TRACESTATE_VENDOR_SEPARATOR);
                }
                builder.append(String.join(TRACESTATE_VALUE_SEPARATOR, field.getKey(), field.getValue().toString()));
                first[0] = false;
            });

        final String traceState = HONEYCOMB_VENDOR_PREFIX + Base64.getEncoder().encodeToString(builder.toString().getBytes(UTF_8));
        final Map<String, String> headers = new HashMap<>();
        headers.put(W3C_TRACEPARENT_HEADER, traceParent);
        headers.put(W3C_TRACESTATE_HEADER, traceState);
        return Optional.of(headers);
    }
}
