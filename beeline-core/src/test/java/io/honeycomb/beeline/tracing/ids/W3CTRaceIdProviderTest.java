package io.honeycomb.beeline.tracing.ids;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class W3CTRaceIdProviderTest {
    @Test(expected = UnsupportedOperationException.class)
    public void checkThatValidUUIDsAreProduced() {
        W3CTraceIdProvider.getInstance().generateId();
    }

    @Test
    public void checkThatValidTraceIdsAreCreated() {
        String traceId = W3CTraceIdProvider.getInstance().generateTraceId();

        assertThat(traceId).isNotNull();
        assertThat(traceId.length()).isEqualTo(32);
        assertThat(traceId).isEqualTo(traceId.toLowerCase());
    }

    @Test
    public void checkThatValidSpanIdsAreCreated() {
        String spanId = W3CTraceIdProvider.getInstance().generateSpanId();

        assertThat(spanId).isNotNull();
        assertThat(spanId.length()).isEqualTo(16);
        assertThat(spanId).isEqualTo(spanId.toLowerCase());
    }
}
