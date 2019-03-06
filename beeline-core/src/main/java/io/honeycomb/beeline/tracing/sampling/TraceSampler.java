package io.honeycomb.beeline.tracing.sampling;

/**
 * Simple interface to test a given input and decide whether to sample it.
 *
 * <h1>Thread-safety</h1>
 * Implementations must be thread-safe and so that they can be shared.
 *
 * @param <T> type of input.
 */
public interface TraceSampler<T> {
    /**
     * Decides whether to sample the input.
     * If it returns 0, it should not be sampled.
     * If positive then it should be sampled and the concrete value represents the {@code sampleRate}.
     *
     * @param input to test.
     * @return Positive int if the input is to be sampled.
     */
    int sample(T input);
}
