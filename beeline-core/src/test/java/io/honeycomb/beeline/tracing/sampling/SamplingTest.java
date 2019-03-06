package io.honeycomb.beeline.tracing.sampling;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplingTest {

    @Test
    public void checkThatAlwaysSamplerIsAlwaysTrue() {
        for (int i = 0; i < 100; i++) {
            assertThat(Sampling.alwaysSampler().sample(UUID.randomUUID())).isGreaterThan(0);
        }
    }

    @Test
    public void checkThatNeverSamplerIsAlwaysFalse() {
        for (int i = 0; i < 100; i++) {
            assertThat(Sampling.neverSampler().sample(UUID.randomUUID())).isZero();
        }
    }

    @Test
    public void deterministicSampler() {
        assertThat(Sampling.deterministicSampler(1)).isInstanceOf(DeterministicTraceSampler.class);
    }
}
