package io.honeycomb.beeline.tracing.context;

import io.honeycomb.beeline.tracing.TracerSpan;

import java.util.Deque;

/**
 * An interface that allows the user to configure how spans in a trace are collected by the
 * {@link io.honeycomb.beeline.tracing.Tracer}.
 * <p>
 * In most cases, the {@link ThreadLocalTracingContext} will be preferrable, and indeed is the default when no special
 * configuration is given. However, in certain cases (eg. when tracing inside a multi-threaded monolithic application),
 * more complex span tracking may be needed in order to overcome {@link ThreadLocal} limitations with long-running
 * thread pools.
 *
 * @see io.honeycomb.beeline.tracing.Tracer
 */
public interface TracingContext
{
    /**
     * Return the stack of spans in the current trace (for this service).
     * @return The current {@link Deque} of spans. <b>Never null.</b>
     */
    Deque<TracerSpan> get();

    /**
     * The size of the current span stack
     * @return int value, zero-to-positive
     */
    int size();

    /**
     * Grab the founding Span in the current stack without removing it.
     * @return The first Span in this service's trace.
     */
    TracerSpan peekLast();

    /**
     * Grab the latest Span in the current stack without removing it.
     * @return The first Span in this service's trace.
     */
    TracerSpan peekFirst();

    /**
     * Check whether the current Span stack if empty.
     * @return True if the stack is empty (or missing). False otherwise.
     */
    boolean isEmpty();

    /**
     * Add a new Span to the current service-scope of the trace.
     * @param span The span to add
     */
    void push( TracerSpan span );

    /**
     * Pop the latest Span off the stack for the current service trace and return it.
     * @return the popped value, in case it's useful for debugging, etc.
     */
    TracerSpan pop();
}
