package io.honeycomb.beeline.tracing.propagation;

import org.mockito.junit.MockitoJUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class AWSPropagationCodecTest {

    private AWSPropagationCodec codec;

    @Before
    public void setUp() {
        codec = new AWSPropagationCodec();
    }

    // Decode

    @Test
    public void GIVEN_aNullParameter_EXPECT_anEmptyContext() {
        final PropagationContext context = codec.decode(null);

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_amEmptyParameter_EXPECT_anEmptyContext() {
        final PropagationContext context = codec.decode(Map.of("", ""));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aValidTraceValue_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "root=123;parent=abc;foo=bar;userId=123;toRetry=true";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("abc");
        assertThat(context.getTraceFields().size()).isEqualTo(3);
        assertThat(context.getTraceFields()).contains(entry("foo", "bar"),
                                                      entry("userId", "123"),
                                                      entry("toRetry", "true"));
    }

    @Test
    public void GIVEN_aValidTraceValueWithSelf_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "root=123;self=abc;foo=bar;userId=123;toRetry=true";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("abc");
        assertThat(context.getTraceFields().size()).isEqualTo(3);
        assertThat(context.getTraceFields()).contains(entry("foo", "bar"),
                                                      entry("userId", "123"),
                                                      entry("toRetry", "true"));
    }

    @Test
    public void GIVEN_aValidTraceValueWithMixedCasing_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "rOoT=123;PaReNt=abc;foo=bar;userId=123;toRetry=true";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("abc");
        assertThat(context.getTraceFields().size()).isEqualTo(3);
        assertThat(context.getTraceFields()).contains(entry("foo", "bar"),
                                                      entry("userId", "123"),
                                                      entry("toRetry", "true"));
    }

    @Test
    public void GIVEN_aValidTraceValueWithSelfAndMixedCasing_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "rOoT=123;sElF=abc;foo=bar;userId=123;toRetry=true";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("abc");
        assertThat(context.getTraceFields().size()).isEqualTo(3);
        assertThat(context.getTraceFields()).contains(entry("foo", "bar"),
                                                      entry("userId", "123"),
                                                      entry("toRetry", "true"));
    }

    @Test
    public void GIVEN_aTraceHeaderWhereKVsareInReverseOrder_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "foo=bar;userId=123;toRetry=true;parent=abc;root=123";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("abc");
        assertThat(context.getTraceFields().size()).isEqualTo(3);
        assertThat(context.getTraceFields()).contains(entry("foo", "bar"),
                                                      entry("userId", "123"),
                                                      entry("toRetry", "true"));
    }

    @Test
    public void GIVEN_aTraceHeaderWithSelfParentRoot_EXPECT_toDecodeCorrectlyWithLastValueWins() {
        final String traceHeader = "root=123;parent=abc;self=baz";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("baz");
        assertThat(context.getTraceFields().isEmpty());
    }

    @Test
    public void GIVEN_aTraceHeaderWithSelfRootParent_EXPECT_toDecodeCorrectlyWithLastValueWins() {
        final String traceHeader = "root=123;self=baz;parent=abc;";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("abc");
        assertThat(context.getTraceFields().isEmpty());
    }

    @Test
    public void GIVEN_aTraceHeaderWithoutParentOrSelf_EXPECT_toDecodeCorrectlySpanIdAsTraceId() {
        final String traceHeader = "root=123";
        final PropagationContext context = codec.decode(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("123");
        assertThat(context.getSpanId()).isEqualTo("123");
    }

    // Encode

    @Test
    public void GIVEN_anEmptyContext_EXPECT_empty() {
        final Optional<Map<String,String>> encoded = codec.encode(PropagationContext.emptyContext());

        assertThat(encoded).isEmpty();
    }

    @Test
    public void GIVEN_aPopulatedContext_EXPECT_aValidHeaderValue() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("123", "abc", null, Collections.singletonMap("test", "value"))).get();

        assertThat(encoded).isEqualTo(Map.of(AWSPropagationCodec.AWS_TRACE_HEADER, "root=123;parent=abc;test=value"));
    }
}
