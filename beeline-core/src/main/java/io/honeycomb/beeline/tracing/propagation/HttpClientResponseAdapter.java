package io.honeycomb.beeline.tracing.propagation;

import java.util.Optional;

/**
 * Adapt an HTTP response that has been received by a client. This is so it can be
 * processed by an {@link HttpClientPropagator}.
 */
public interface HttpClientResponseAdapter {
    /**
     * Return the HTTP status code as an integer.
     * @return the HTTP status code.
     */
    int getStatus();
    /**
     * Return the first header value for the header name.
     * @param name the header name
     * @return the first header value, or {@code Optional.empty()} if none
     */
    Optional<String> getFirstHeader(String name);
}
