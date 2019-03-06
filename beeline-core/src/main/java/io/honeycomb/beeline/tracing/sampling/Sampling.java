package io.honeycomb.beeline.tracing.sampling;

/**
 * Contains helpers for trace sampling strategies.
 */
public final class Sampling {
    private static final TraceSampler<Object> ALWAYS_SAMPLER = new AlwaysSampler();
    private static final TraceSampler<Object> NEVER_SAMPLER = new NeverSampler();

    private Sampling() {
        // utils class
    }

    /**
     * A dummy sampler that will always sample.
     *
     * @return sampler.
     */
    public static TraceSampler<Object> alwaysSampler() {
        return ALWAYS_SAMPLER;
    }

    /**
     * A dummy sampler that will never sample.
     *
     * @return sampler.
     */
    public static TraceSampler<Object> neverSampler() {
        return NEVER_SAMPLER;
    }

    /**
     * The standard sampling strategy used across Beelines.
     *
     * @param sampleRate to use.
     * @return trace sampler.
     * @see DeterministicTraceSampler
     */
    public static TraceSampler<String> deterministicSampler(final int sampleRate) {
        return new DeterministicTraceSampler(sampleRate);
    }

    private static class AlwaysSampler implements TraceSampler<Object> {
        @Override
        public int sample(final Object input) {
            return 1;
        }
    }

    private static class NeverSampler implements TraceSampler<Object> {
        @Override
        public int sample(final Object input) {
            return 0;
        }}
}
