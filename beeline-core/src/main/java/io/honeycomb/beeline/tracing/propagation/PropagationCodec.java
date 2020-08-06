package io.honeycomb.beeline.tracing.propagation;

import java.util.Optional;

/**
 * Interface that allows conversion between in-memory {@link PropagationContext} and some transmission format of
 * trace data, such as an HTTP header.
 * {@link Propagation} contains helpers for this.
 * <p>
 * Since tracing instrumentation should not be invasive, implementations of this interface must avoid throwing
 * exceptions, and instead return "empty" return values - highlight issues through logging.
 *
 * <h1>Thread-safety</h1>
 * Implementations must be thread-safe and so that they can be shared.
 *
 * @param <E> Type of the transmission format to be decoded/encoded.
 */
public interface PropagationCodec<E> {

    /**
     * Returns the name of the codec.
     *
     * @return the codec name.
     */
    String getName();

    /**
     * Decode some transmission format into a propagation context.
     *
     * @param encodedTrace to decode to trace.
     * @return context - "empty" context if parameter is invalid.
     */
    PropagationContext decode(E encodedTrace);

    /**
     * Encode the propagation context into a transmission format.
     *
     * @param context to encode
     * @return encoded context - "empty" optional if invalid or not currently on a trace.
     */
    Optional<E> encode(PropagationContext context);
}
