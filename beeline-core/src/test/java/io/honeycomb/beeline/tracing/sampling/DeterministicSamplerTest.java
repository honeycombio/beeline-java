package io.honeycomb.beeline.tracing.sampling;

import org.assertj.core.data.Percentage;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Based on test cases from the nodejs beeline.
 * https://github.com/honeycombio/beeline-nodejs/blob/main/lib/deterministic_sampler.test.js
 */
public class DeterministicSamplerTest {

    private DeterministicTraceSampler sampler;

    @Test
    public void samplerShouldRejectNegativeSampleRate() {
        assertThatThrownBy(() -> new DeterministicTraceSampler(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void checkSamplerWithSampleDataPoints() {
        sampler = new DeterministicTraceSampler(17);

        assertThat(sampler.sample("hello")).isZero();
        assertThat(sampler.sample("hello")).isZero();
        assertThat(sampler.sample("world")).isZero();
        assertThat(sampler.sample("this5")).isGreaterThan(0);
    }

    @Test
    public void testThatVariousSampleRatesAreWithinExpectedBounds() {
        final int[] testSampleRates = {2, 10, 20};
        final int numberOfRequestIDsToTest = 50000;
        final Percentage acceptableMarginOfError = Percentage.withPercentage(5.0);

        for (int sampleRate : testSampleRates) {
            sampler = new DeterministicTraceSampler(sampleRate);
            int nSampled = 0;

            for (int i = 0; i < numberOfRequestIDsToTest; i++) {
                if (sampler.sample(randomRequestID()) > 0) {
                    nSampled++;
                }
            }

            // Sampling should be balanced across all request IDs regardless of sample rate.
            // If we cross this threshold, flunk the test.
            double expectedNSampled = (double) numberOfRequestIDsToTest / (double) sampleRate;
            assertThat((double) nSampled).isCloseTo(expectedNSampled, acceptableMarginOfError);
        }
    }

    @Test
    public void checkThatASampleRateOfOneSamplesAll() {
        final int nRequestIDs = 50_000;

        final int sampleRate = 1;
        sampler = new DeterministicTraceSampler(sampleRate);

        int nSampled = 0;
        for (int i = 0; i < nRequestIDs; i++) {
            if (sampler.sample(randomRequestID()) > 0) {
                nSampled++;
            }
        }

        // should sample all
        assertThat(nSampled).isEqualTo(50_000);
    }

    @Test
    public void checkThatASampleRateOfZeroSamplesNone() {
        final int nRequestIDs = 50_000;

        final int sampleRate = 0;
        sampler = new DeterministicTraceSampler(sampleRate);

        int nSampled = 0;
        for (int i = 0; i < nRequestIDs; i++) {
            if (sampler.sample(randomRequestID()) > 0) {
                nSampled++;
            }
        }

        // should sample all
        assertThat(nSampled).isEqualTo(0);
    }

    @Test
    public void checkThatSamplerGivesConsistentAnswers() {
        final DeterministicTraceSampler samplerA = new DeterministicTraceSampler(3);
        final DeterministicTraceSampler samplerB = new DeterministicTraceSampler(3);
        final String sampleString = UUID.randomUUID().toString();
        final int firstAnswer = samplerA.sample(sampleString);

        // sampler should not give different answers for subsequent runs
        for (int i = 0; i < 25; i++) {
            final int answerA = samplerA.sample(sampleString);
            final int answerB = samplerB.sample(sampleString);
            assertThat(answerA).isEqualTo(firstAnswer);
            assertThat(answerB).isEqualTo(firstAnswer);
        }
    }

    private static final String requestIDBytes = "abcdef0123456789";

    /**
     * Comment from node tests:
     * <pre>
     * // create request ID roughly resembling something you would get from
     * // AWS ALB, e.g.,
     * //
     * // 1-5ababc0a-4df707925c1681932ea22a20
     * //
     * // The AWS docs say the middle bit is "time in seconds since epoch",
     * // (implying base 10) but the above represents an actual Root= ID from
     * // an ALB access log, so... yeah.
     * </pre>
     */
    private String randomRequestID() {
        final StringBuilder reqID = new StringBuilder("1-");
        for (int i = 0; i < 8; i++) {
            final int charToPick = getRandomInt(requestIDBytes.length());
            reqID.append(requestIDBytes.charAt(charToPick));
        }
        reqID.append("-");
        for (int i = 0; i < 24; i++) {
            final int charToPick = getRandomInt(requestIDBytes.length());
            reqID.append(requestIDBytes.charAt(charToPick));
        }
        return reqID.toString();
    }

    private int getRandomInt(final int max) {
        return (int) (Math.random() * max);
    }
}
