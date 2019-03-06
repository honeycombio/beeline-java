package io.honeycomb.libhoney.transport.json;

import java.io.IOException;

public interface JsonDeserializer<T> {
    /**
     * Deserialize JSON encoded in UTF-8 bytes as a 'T'.
     *
     * @param data to deserialize - must not be valid JSON data.
     * @return Java object that represents the input JSON.
     * @throws IOException if any error occurs during deserialization.
     */
    T deserialize(final byte[] data) throws IOException;
}
