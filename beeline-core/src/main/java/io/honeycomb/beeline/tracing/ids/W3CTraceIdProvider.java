package io.honeycomb.beeline.tracing.ids;

import java.security.SecureRandom;

import static io.honeycomb.libhoney.utils.ObjectUtils.isNullOrEmpty;

/**
 * Creates Trace and Span IDs that conform to the W3C specification.
 * This is inline with Beeline instrumentations in other programming languages.
 *
 * <p>Thread-safety</p>
 * Instances of this class are thread-safe and can be shared.
 */
public class W3CTraceIdProvider implements TraceIdProvider {
    private static final TraceIdProvider INSTANCE = new W3CTraceIdProvider();
    private static final SecureRandom RAND = new SecureRandom();

    private static final int TRACEID_BYTES_LENGTH = 16;
    private static final int SPANID_BYTES_LENGTH = 8;

    private static final String INVALID_TRACEID = "00000000000000000000000000000000";
    private static final int TRACEID_STRING_LENGTH = INVALID_TRACEID.length();
    private static final String INVALID_SPANID = "0000000000000000";
    private static final int SPANID_STRING_LENGTH = INVALID_SPANID.length();

    public static TraceIdProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public String generateId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String generateTraceId() {
        byte[] bytes = new byte[TRACEID_BYTES_LENGTH];
        RAND.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    @Override
    public String generateSpanId() {
        byte[] bytes = new byte[SPANID_BYTES_LENGTH];
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
     * @param traceId the trace ID to validate.
     * @throws IllegalArgumentException
     *         If the traceId is not valid.
     */
    public static void validateTraceId(String traceId) {
        if (!isValidTraceId(traceId)) {
            throw new IllegalArgumentException("Invalid TraceID string: " + traceId);
        }
    }

    /**
     * Validates the provided traceId.
     * @param traceId the trace ID to validate.
     * @return boolean whether the traceId is valid or not.
     */
    public static Boolean isValidTraceId(String traceId) {
        return !isNullOrEmpty(traceId) &&
               traceId.length() == TRACEID_STRING_LENGTH &&
               !traceId.equals(INVALID_TRACEID) &&
               isHexString(traceId);
    }

    /**
     * Validates the provided spanId.
     * Intended for internal use only.
     * @param spanId the span ID to validate.
     * @throws IllegalArgumentException
     *         If the spanId is not valid.
     */
    public static void validateSpanId(String spanId) {
        if (!isValidSpanId(spanId)) {
            throw new IllegalArgumentException("Invalid SpanID string: " + spanId);
        }
    }

    /**
     * Validates the provided spanId.
     * @param spanId the span ID to validate.
     * @return boolean whether the spanId is valid or not.
     */
    public static Boolean isValidSpanId(String spanId) {
        return !isNullOrEmpty(spanId) &&
               spanId.length() == SPANID_STRING_LENGTH &&
               !spanId.equals(INVALID_SPANID) &&
               isHexString(spanId);
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
