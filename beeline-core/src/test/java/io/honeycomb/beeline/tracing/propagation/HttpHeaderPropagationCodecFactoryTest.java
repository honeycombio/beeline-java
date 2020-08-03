package io.honeycomb.beeline.tracing.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

public class HttpHeaderPropagationCodecFactoryTest {
    @Test
    public void GIVEN_nullPropagtorNames_EXPECT_honeycombV1Propagator() {
        assertThat(HttpHeaderPropagationCodecFactory.create(null)).isEqualTo(Propagation.honeycombHeaderV1());
    }

    @Test
    public void GIVEN_emptyPropagatorNames_EXPECT_honeycombV1Propagator() {
        assertThat(HttpHeaderPropagationCodecFactory.create(Collections.emptyList())).isEqualTo(Propagation.honeycombHeaderV1());
    }

    @Test
    public void GIVEN_invalidPropagatorNames_EXPECT_honeycombV1Propagator() {
        assertThat(HttpHeaderPropagationCodecFactory.create(Collections.singletonList("unknown"))).isEqualTo(Propagation.honeycombHeaderV1());
    }

    @Test
    public void GIVEN_awsAsPropagatorName_EXPECT_singleImplementationOfPropagator() {
        assertThat(HttpHeaderPropagationCodecFactory.create(Collections.singletonList(AWSPropagationCodec.CODEC_NAME))).isEqualTo(Propagation.aws());
    }

    @Test
    public void GIVEN_honeyAsPropagatorName_EXPECT_singleImplementationOfPropagator() {
        assertThat(HttpHeaderPropagationCodecFactory.create(Collections.singletonList(HttpHeaderV1PropagationCodec.CODEC_NAME))).isEqualTo(Propagation.honeycombHeaderV1());
    }

    @Test
    public void GIVEN_w3cAsPropagatorName_EXPECT_singleImplementationOfPropagator() {
        assertThat(HttpHeaderPropagationCodecFactory.create(Collections.singletonList(W3CPropagationCodec.CODEC_NAME))).isEqualTo(Propagation.w3c());
    }

    @Test
    public void GIVEN_multipleValidPropagatorName_EXPECT_compositePropagator() {
        PropagationCodec<Map<String, String>> codec = HttpHeaderPropagationCodecFactory.create(Arrays.asList(W3CPropagationCodec.CODEC_NAME, AWSPropagationCodec.CODEC_NAME));
        assertThat(codec).isInstanceOf(CompositeHttpHeaderPropagtor.class);
        assertThat(codec.getName()).isEqualTo(String.join(",", W3CPropagationCodec.CODEC_NAME, AWSPropagationCodec.CODEC_NAME));
    }
}
