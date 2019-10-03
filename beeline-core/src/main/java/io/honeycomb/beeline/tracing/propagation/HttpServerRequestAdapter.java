package io.honeycomb.beeline.tracing.propagation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapt an HTTP request that has been received by a server. This is so it can be
 * processed by an {@link HttpServerPropagator}.
 */
public interface HttpServerRequestAdapter {
    /**
     * Returns the HTTP method of the request as a plain String.
     * @return the HTTP method
     */
    String getMethod();

    /**
     * Returns the path component of this URI.
     *
     * @return  The path component of this URI,
     *          or {@code Optional.empty()} if the path is undefined
     */
    Optional<String> getPath();

    /**
     * Return the first header value for the header name.
     * @param name the header name
     * @return the first header value, or {@code Optional.empty()} if none
     */
    Optional<String> getFirstHeader(String name);

    /**
     * Return the URI scheme.
     * @return the scheme. Can return {@code Optional.empty()} if not available.
     */
    Optional<String> getScheme();

    /**
     * Return the host in the request.
     * @return the host. Can return {@code Optional.empty()} if not available.
     */
    Optional<String> getHost();

    /**
     * Returns the name and version of the protocol. For instance, HTTP/1.1.
     *
     * @return the HTTP version
     */
    String getHttpVersion();

    /**
     * Indicates whether this request was made using a secure channel, such as HTTPS.
     *
     * @return whether the request is secure
     */
    boolean isSecure();

    /**
     * Returns the IP address of the client (or final proxy) that sent the request.
     * @return the remote address
     */
    String getRemoteAddress();

    /**
     * Return the map of query parameters.
     *
     * @return the map, which may be empty if there are no query parameters.
     */
    Map<String, List<String>> getQueryParams();

    /**
     * Return the length in bytes of the request body.
     * @return the request content length
     */
    int getContentLength();
}
