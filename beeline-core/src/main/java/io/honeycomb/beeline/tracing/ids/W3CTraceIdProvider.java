package io.honeycomb.beeline.tracing.ids;

import java.util.Random;

/**
 * Creates Trace and Span IDs that conform to the W3C specification.
 * This is inline with Beeline instrumentations in other programming languages.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared.
 */
public class W3CTraceIdProvider implements TraceIdProvider {
    private static final TraceIdProvider INSTANCE = new W3CTraceIdProvider();
    private static final Random RAND = new Random();
    private static final int TRACEID_LENGTH = 16;
    private static final int SPANID_LENGTH = 8;

    public static TraceIdProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public String generateId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String generateTraceId() {
        byte[] bytes = new byte[TRACEID_LENGTH];
        RAND.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    @Override
    public String generateSpanId() {
        byte[] bytes = new byte[SPANID_LENGTH];
        RAND.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    // Taken from Stackoverflow post: https://stackoverflow.com/a/9855338/329666
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Validates the provided traceId.
     * Intended for internal use only.
     * @param traceId
     * @throws IllegalArgumentException
     *         If the traceId is not valid.
     */
    public static void validateTraceId(String traceId) {
        if (traceId.isEmpty() ||
            traceId.length() != 32 ||
            !isHexString(traceId)) {
            throw new IllegalArgumentException("Invalid TraceID string: " + traceId);
        }
    }

    /**
     * Validates the provided spanId.
     * Intended for internal use only.
     * @param traceId
     * @throws IllegalArgumentException
     *         If the spanId is not valid.
     */
    public static void validateSpanId(String spanId) {
        if (spanId.isEmpty() ||
            spanId.length() != 16 ||
            !isHexString(spanId)) {
            throw new IllegalArgumentException("Invalid SpanID string: " + spanId);
        }
    }

    private static Boolean isHexString(String id) {
        for (int i = 0; i < id.length(); i++) {
            if ( Character.digit(id.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }
}
