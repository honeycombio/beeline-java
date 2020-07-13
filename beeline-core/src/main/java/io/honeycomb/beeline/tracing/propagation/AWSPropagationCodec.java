package io.honeycomb.beeline.tracing.propagation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.honeycomb.libhoney.utils.ObjectUtils.isNullOrEmpty;

/**
 * Codec that can decode/encode trace context based on the AWS http header ('X-Amzn-Trace-Id').
 * Prefer the {@link Propagation#aws()} factory method to retrieve an instance.
 * <p>
 * The design of this class avoids throwing exceptions in favour of logging warnings and returning null on encode
 * or an "empty context" on decode.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared.
 */
public class AWSPropagationCodec implements PropagationCodec<Map<String, String>> {

    private static final AWSPropagationCodec INSTANCE = new AWSPropagationCodec();

    // @formatter:off
    protected static final String AWS_TRACE_HEADER          = "X-Amzn-Trace-Id";

    private static final String SEGMENT_SEPARATOR           = ";";
    private static final Pattern SPLIT_SEGMENTS_PATTERN     = Pattern.compile(SEGMENT_SEPARATOR);
    private static final String KV_SEPARATOR                = "=";
    private static final Pattern SPLIT_KV_PATTERN           = Pattern.compile(KV_SEPARATOR);
    private static final String ROOT_KEY                    = "root";
    private static final String PARENT_KEY                  = "parent";
    private static final String SELF_KEY                    = "self";

    // StringBuilder extends it's internal buffer when capacity is exceeded so better to set a bigger
    // starting capacity - may need to tune this in the future
    private static final int DEFAULT_STRINGBUILDER_CAPACITY = 1024;
    // @formatter:on

    public static AWSPropagationCodec getInstance() {
        return INSTANCE;
    }

    /**
     * This decodes the 'X-Amzn-Trace-Id' header of the following format:
     * <p>
     * {@code root={traceId};parent={parentId};{key=value... traceFields}}
     * <p>
     * root is required to create a non-empty Propagation context, while parent and key-value pairs are
     * treated as optional.
     * <p>
     * self can be used in place of parent. Note that if both parent and self are present, the last entry
     * will be used.
     * </p>
     *
     * @param encodedTrace to decode into a {@link PropagationContext}.
     * @return extracted context - "empty" context if encodedTrace value has an invalid format or is null.
     */
    @Override
    public PropagationContext decode(Map<String, String> headers) {
        if (headers == null || !headers.containsKey(AWS_TRACE_HEADER)) {
            return PropagationContext.emptyContext();
        }
        final String encodedTrace = headers.getOrDefault(AWSPropagationCodec.AWS_TRACE_HEADER, null);
        if (isNullOrEmpty(encodedTrace)) {
            return PropagationContext.emptyContext();
        }

        final String[] segments = SPLIT_SEGMENTS_PATTERN.split(encodedTrace);
        String traceId = null;
        String parentSpanId = null;
        Map<String, Object> traceFields = new HashMap<String, Object>();
        for (final String keyValue : segments) {
            final String[] keyAndValue = SPLIT_KV_PATTERN.split(keyValue, 2);

            // prefer if/else for case insensitive comparisons
            if (keyAndValue[0].equalsIgnoreCase(ROOT_KEY)) {
                traceId = keyAndValue[1];
            }
            else if (keyAndValue[0].equalsIgnoreCase(SELF_KEY) || keyAndValue[0].equalsIgnoreCase(PARENT_KEY)) {
                parentSpanId = keyAndValue[1];
            }
            else // all other fields are to be treated as string-string trace fields
            {
                traceFields.put(keyAndValue[0], keyAndValue[1]);
            }
        }

        // If no header is provided to an ALB or ELB, it will generate a header
        // with a Root field and forwards the request. In this case it should be
        // used as both the parent id and the trace id.
        if (!isNullOrEmpty(traceId) && isNullOrEmpty(parentSpanId)) {
            parentSpanId = traceId;
        }

        return new PropagationContext(traceId, parentSpanId, null, traceFields);
    }

    /**
     * This encodes the given trace context in the format accepted by the 'X-Amzn-Trace-Id' header.
     * This will return null if the span id or trace id are not set (i.e. the context is not an active trace).
     *
     * @param context to encode into a valid header value.
     * @return a valid AWS http header value - empty if required IDs are missing or input is null.
     */
    @Override
    public Optional<Map<String, String>> encode(PropagationContext context) {
        if (context == null || isNullOrEmpty(context.getSpanId()) || isNullOrEmpty(context.getTraceId())) {
            return Optional.empty();
        }

        final StringBuilder builder = new StringBuilder(DEFAULT_STRINGBUILDER_CAPACITY)
            .append(ROOT_KEY).append(KV_SEPARATOR).append(context.getTraceId()).append(SEGMENT_SEPARATOR)
            .append(PARENT_KEY).append(KV_SEPARATOR).append(context.getSpanId());

        if (!context.getTraceFields().isEmpty()) {
            builder.append(SEGMENT_SEPARATOR);
            for (Map.Entry<String, Object> entry : context.getTraceFields().entrySet()) {
                builder.append(entry.getKey()).append(KV_SEPARATOR).append(entry.getValue().toString());
            }
        }

        return Optional.of(
            Map.of(AWS_TRACE_HEADER, builder.toString())
        );
    }
}
