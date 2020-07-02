package io.honeycomb.beeline.tracing.propagation;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

public class W3CPropagationCodecTest {

    private W3CPropagationCodec codec = W3CPropagationCodec.getInstance();

    @Before
    public void setUp() {
        codec = new W3CPropagationCodec();
    }

    // Decode

    @Test
    public void GIVEN_aNullParameter_EXPECT_anEmptyContext() {
        final PropagationContext context = codec.decode(null);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_amEmptyParameter_EXPECT_anEmptyContext() {
        final PropagationContext context = codec.decode("");

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aValidTraceValue_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-00";
        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
    }

    @Test
    public void GIVEN_aValidTraceValueWithSampledTraceFlag_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
    }

    @Test
    public void GIVEN_aTraceHeaderTooFewSegments_EXPECT_anEmptyContext() {
        final String traceHeader = "1-2-3";

        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderTooManySegments_EXPECT_anEmptyContext() {
        final String traceHeader = "1-2-3-4-5";

        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }


    @Test
    public void GIVEN_aTraceHeaderWithInvalidVersion_EXPECT_anEmptyContext() {
        final String traceHeader = "invalid-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-00";

        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderWithInvalidTraceId_EXPECT_anEmptyContext() {
        final String traceHeader = "00-invalid-ace1ecab581fc069-00";

        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderWithInvalidSpanId_EXPECT_anEmptyContext() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-invalid-00";

        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderWithInvalidTraceFlags_EXPECT_anEmptyContext() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-invalid";

        final PropagationContext context = codec.decode(traceHeader);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    // Encode

    @Test
    public void GIVEN_anEmptyContext_EXPECT_empty() {
        final Optional<String> encoded = codec.encode(PropagationContext.emptyContext());

        assertThat(encoded).isEmpty();
    }

    @Test
    public void GIVEN_aPopulatedContext_EXPECT_aValidHeaderValue() {
        String traceId = "4cbc8d50f02449e887e8bc2aa8020d26";
        String spanId = "ace1ecab581fc069";
        final String encoded = codec.encode(new PropagationContext(traceId, spanId, null, null)).get();

        assertThat(encoded).isEqualTo(String.join("-", "00", traceId, spanId, "00"));
    }
}
