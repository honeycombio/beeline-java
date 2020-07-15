package io.honeycomb.beeline.tracing.propagation;

import java.util.Optional;

/**
 * Adapt an HTTP request that is about to be sent by a client. This is so it can be
 * processed by an {@link HttpClientPropagator}.
 */
public interface HttpClientRequestAdapter extends HttpRequestAdapter {
    /**
     * Returns the HTTP method of the request.
     * @return the HTTP method
     */
    String getMethod();
    /**
     * Returns the path requested.
     *
     * @return  The path,
     *          or {@code Optional.empty()} if the path is undefined
     */
    Optional<String> getPath();
    /**
     * Return the length in bytes of the request body.
     * @return the request content length
     */
    int getContentLength();
    /**
     * Return the first header value for the header name.
     * @param name the header name
     * @return the first header value, or {@code Optional.empty()} if none
     */
    Optional<String> getFirstHeader(String name);
    /**
     * Add a header to the request
     * @param name header name
     * @param value header value
     */
    void addHeader(String name, String value);
}
