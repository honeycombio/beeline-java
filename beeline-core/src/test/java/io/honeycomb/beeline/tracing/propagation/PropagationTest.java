package io.honeycomb.beeline.tracing.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropagationTest {
    @Test
    public void honeycombHeaderV1() {
        assertThat(Propagation.honeycombHeaderV1()).isInstanceOf(HttpHeaderV1PropagationCodec.class);
    }
}
