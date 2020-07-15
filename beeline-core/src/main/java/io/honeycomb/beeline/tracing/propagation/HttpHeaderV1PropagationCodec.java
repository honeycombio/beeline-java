package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.libhoney.shaded.com.fasterxml.jackson.core.type.TypeReference;
import io.honeycomb.libhoney.shaded.com.fasterxml.jackson.databind.ObjectReader;
import io.honeycomb.libhoney.shaded.com.fasterxml.jackson.databind.ObjectWriter;
import io.honeycomb.libhoney.transport.json.JsonDeserializer;
import io.honeycomb.libhoney.transport.json.JsonSerializer;
import io.honeycomb.libhoney.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.honeycomb.libhoney.utils.ObjectUtils.isNullOrEmpty;

/**
 * Codec that can decode/encode trace context based on Version 1 of the honeycomb http header ('x-honeycomb-trace').
 * Prefer the {@link Propagation#honeycombHeaderV1()} factory method to retrieve an instance.
 * <p>
 * The design of this class avoids throwing exceptions in favour of logging warnings and returning null on encode
 * or an "empty context" on decode.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared.
 */
public class HttpHeaderV1PropagationCodec implements PropagationCodec<Map<String, String>> {
    private static final Logger LOG = LoggerFactory.getLogger(HttpHeaderV1PropagationCodec.class);
    private static final HttpHeaderV1PropagationCodec INSTANCE = new HttpHeaderV1PropagationCodec();

    // @formatter:off
    protected static final String HONEYCOMB_TRACE_HEADER    = "x-honeycomb-trace";

    private static final String TRACE_CONTEXT_VERSION_ONE   = "1";
    private static final String PAYLOAD_SEPARATOR           = ",";
    private static final String VERSION_PAYLOAD_SEPARATOR   = ";";
    private static final String KV_SEPARATOR                = "=";
    private static final Pattern SPLIT_VERSION_AND_PAYLOAD  = Pattern.compile(VERSION_PAYLOAD_SEPARATOR);
    private static final Pattern SPLIT_PAYLOAD              = Pattern.compile(PAYLOAD_SEPARATOR);
    private static final Pattern SPLIT_KV                   = Pattern.compile(KV_SEPARATOR);
    private static final String TRACE_ID_KEY                = "trace_id";
    private static final String PARENT_ID_KEY               = "parent_id";
    private static final String DATASET_KEY                 = "dataset";
    private static final String CONTEXT_KEY                 = "context";
    private static final int EXTRA_STRING_BUILDER_CAPACITY  = 16;
    // @formatter:on

    private final JsonSerializer<? super Map<String, ?>> serializer;

    private final JsonDeserializer<? extends Map<String, Object>> deserializer;

    public HttpHeaderV1PropagationCodec(final JsonSerializer<? super Map<String, ?>> serializer,
                                        final JsonDeserializer<? extends Map<String, Object>> deserializer) {
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    public static HttpHeaderV1PropagationCodec getInstance() {
        return INSTANCE;
    }

    public HttpHeaderV1PropagationCodec() {
        final DefaultJsonConverter converter = new DefaultJsonConverter();
        this.serializer = converter;
        this.deserializer = converter;
    }

    /**
     * This decodes the 'x-honeycomb-trace' header of the following format:
     * <p>
     * {@code 1;trace_id={traceId},parent_id={parentId},dataset={dataset},context={Base64-encoded json}}
     * <p>
     * Trace_id and parent_id are required to create a non-empty Propagation context, while dataset and context are
     * treated as optional.
     *
     * @param encodedTrace to decode into a {@link PropagationContext}.
     * @return extracted context - "empty" context if encodedTrace value has an invalid format or is null.
     */
    @SuppressWarnings({"OverlyComplexMethod", "OverlyLongMethod"})
    @Override
    public PropagationContext decode(final Map<String, String> headers) {
        if (headers == null || !headers.containsKey(HONEYCOMB_TRACE_HEADER)) {
            return PropagationContext.emptyContext();
        }
        final String encodedTrace = headers.getOrDefault(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, null);
        if (isNullOrEmpty(encodedTrace)) {
            return PropagationContext.emptyContext();
        }
        final String[] versionAndPayload = SPLIT_VERSION_AND_PAYLOAD.split(encodedTrace, 2);
        if (versionAndPayload.length < 2 || !TRACE_CONTEXT_VERSION_ONE.equals(versionAndPayload[0])) {
            LOG.warn("Invalid honeycomb trace header: {}", encodedTrace);
            return PropagationContext.emptyContext();
        }
        final String[] payloadEntries = SPLIT_PAYLOAD.split(versionAndPayload[1]);
        if (payloadEntries.length < 2) {
            LOG.warn("Invalid honeycomb trace header - missing key-values: {}", encodedTrace);
            return PropagationContext.emptyContext();
        }
        String traceId = null;
        String parentSpanId = null;
        String dataset = null;
        Map<String, Object> traceFields = null;
        for (final String keyValue : payloadEntries) {
            final String[] keyAndValue = SPLIT_KV.split(keyValue, 2);
            switch (keyAndValue[0]) {
                case TRACE_ID_KEY:
                    traceId = keyAndValue[1];
                    break;
                case PARENT_ID_KEY:
                    parentSpanId = keyAndValue[1];
                    break;
                case DATASET_KEY:
                    dataset = decodeDataset(keyAndValue[1]);
                    break;
                case CONTEXT_KEY:
                    traceFields = decodeFields(keyAndValue[1]);
                    break;
                default: // warn but continue loop
                    LOG.debug("Unknown key in trace header: {}", keyAndValue[0]);
                    break;
            }
        }
        if (traceId == null || parentSpanId == null) {
            LOG.warn("Invalid honeycomb trace header - missing IDs: {}", encodedTrace);
            return PropagationContext.emptyContext();
        }
        return new PropagationContext(traceId, parentSpanId, dataset, traceFields);
    }

    private String encodeContext(final Map<String, Object> traceFields) {
        final byte[] serialized;
        try {
            serialized = serializer.serialize(traceFields);
        } catch (final IOException e) {
            LOG.warn("Failure during serialization/encoding of trace fields", e);
            return null;
        }
        return Base64.getEncoder().encodeToString(serialized);
    }

    private Map<String, Object> decodeFields(final String encoded) {
        final byte[] data;
        try {
            data = Base64.getDecoder().decode(encoded);
        } catch (final IllegalArgumentException e) {
            LOG.warn("Extracting trace context failed during Base64 decoding", e);
            return Collections.emptyMap();
        }

        try {
            return deserializer.deserialize(data);
        } catch (final IOException e) {
            LOG.warn("Extracting trace context failed during json deserialization", e);
            return Collections.emptyMap();
        }
    }

    /**
     * This encodes the given trace context in the format accepted by the 'x-honeycomb-trace' header.
     * This will return null if the span id or trace id are not set (i.e. the context is not an active trace).
     *
     * @param context to encode into a valid header value.
     * @return a valid honeycomb trace header value - empty if required IDs are missing or input is null.
     */
    @Override
    public Optional<Map<String, String>> encode(final PropagationContext context) {
        if (context == null || isNullOrEmpty(context.getSpanId()) || isNullOrEmpty(context.getTraceId())) {
            return Optional.empty();
        }

        String contextAsB64 = null;
        if (!context.getTraceFields().isEmpty()) {
            contextAsB64 = encodeContext(context.getTraceFields());
        }

        final StringBuilder stringBuilder = buildString(context, contextAsB64);
        return Optional.of(Collections.singletonMap(HONEYCOMB_TRACE_HEADER, stringBuilder.toString()));
    }

    /**
     * Calculate size of the final string ahead of time, so that we ensure the string builder's capacity and avoid
     * resizing.
     */
    private int getStringLength(final PropagationContext context, final CharSequence contextAsB64) {
        // @formatter:off
        final int kvSepLength      = KV_SEPARATOR.length();
        final int payloadSepLength = PAYLOAD_SEPARATOR.length();

        return
            // add a bit of extra space in case we forget to add a component at some point
            EXTRA_STRING_BUILDER_CAPACITY        +
            TRACE_CONTEXT_VERSION_ONE.length()   + VERSION_PAYLOAD_SEPARATOR.length() +
            TRACE_ID_KEY .length() + kvSepLength + context.getTraceId().length()      + payloadSepLength +
            PARENT_ID_KEY.length() + kvSepLength + context.getSpanId() .length()      +
            (contextAsB64 == null ?
                0 :
                (payloadSepLength + CONTEXT_KEY.length() + kvSepLength + contextAsB64.length())
            ) +
            (context.getDataset() == null ?
                0 :
                (payloadSepLength + DATASET_KEY.length() + kvSepLength + context.getDataset().length())
            );
    }

    private StringBuilder buildString(final PropagationContext context, final String contextAsB64) {
        final int capacity = getStringLength(context, contextAsB64);

        final StringBuilder stringBuilder = new StringBuilder(capacity)
            .append(TRACE_CONTEXT_VERSION_ONE).append(VERSION_PAYLOAD_SEPARATOR)
            .append(TRACE_ID_KEY) .append(KV_SEPARATOR).append(context.getTraceId()).append(PAYLOAD_SEPARATOR)
            .append(PARENT_ID_KEY).append(KV_SEPARATOR).append(context.getSpanId());
        final String dataset = context.getDataset();
        if (dataset != null) {
            final String encodedDataset = encodeDataset(dataset);
            stringBuilder.append(PAYLOAD_SEPARATOR).append(DATASET_KEY).append(KV_SEPARATOR).append(encodedDataset);
        }
        if (contextAsB64 != null) {
            stringBuilder.append(PAYLOAD_SEPARATOR).append(CONTEXT_KEY).append(KV_SEPARATOR).append(contextAsB64);
        }
        return stringBuilder;
        // @formatter:on
    }

    private String encodeDataset(final String dataset) {
        try {
            return URLEncoder.encode(dataset, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) { // very unlikely to happen!
            throw new IllegalStateException("UTF-8 no supported", e);
        }
    }

    private String decodeDataset(final String dataset) {
        try {
            return URLDecoder.decode(dataset, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) { // very unlikely to happen!
            throw new IllegalStateException("UTF-8 no supported", e);
        }
    }

    public static class DefaultJsonConverter
        implements JsonDeserializer<Map<String, Object>>, JsonSerializer<Map<String, ?>> {

        private static final ObjectWriter OBJECT_WRITER;
        private static final ObjectReader OBJECT_READER;

        static {
            final TypeReference<Map<String, ?>> type = new MapTypeReference();
            OBJECT_WRITER = JsonUtils.OBJECT_MAPPER.writerFor(type);
            OBJECT_READER = JsonUtils.OBJECT_MAPPER.readerFor(type);
        }

        @Override
        public Map<String, Object> deserialize(final byte[] data) throws IOException {
            return OBJECT_READER.readValue(data);
        }

        @Override
        public byte[] serialize(final Map<String, ?> data) throws IOException {
            return OBJECT_WRITER.writeValueAsBytes(data);
        }

    }

    private static class MapTypeReference extends TypeReference<Map<String, ?>> {
    }
}
