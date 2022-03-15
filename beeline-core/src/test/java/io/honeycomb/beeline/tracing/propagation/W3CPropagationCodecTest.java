package io.honeycomb.beeline.tracing.propagation;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, ""));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aValidTraceValue_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-00";
        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
        assertThat(context.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aValidTraceValueWithSampledTraceFlag_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
        assertThat(context.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aTraceHeaderTooFewSegments_EXPECT_anEmptyContext() {
        final String traceHeader = "1-2-3";

        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderTooManySegments_EXPECT_anEmptyContext() {
        final String traceHeader = "1-2-3-4-5";

        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }


    @Test
    public void GIVEN_aTraceHeaderWithInvalidVersion_EXPECT_anEmptyContext() {
        final String traceHeader = "invalid-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-00";

        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderWithInvalidTraceId_EXPECT_anEmptyContext() {
        final String traceHeader = "00-invalid-ace1ecab581fc069-00";

        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderWithInvalidSpanId_EXPECT_anEmptyContext() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-invalid-00";

        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderWithInvalidTraceFlags_EXPECT_anEmptyContext() {
        final String traceHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-invalid";

        final PropagationContext context = codec.decode(Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceHeader));

        assertThat(context).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_anEmptyTrateStateHeader_EXPECT_noDatasetOrFields() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
        assertThat(context.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aTrateStateHeaderWithoutHnyVendor_EXPECT_noDatasetOrFields() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "vendor=123";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
        assertThat(context.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aTrateStateHeaderWithemptyHnyVendor_EXPECT_noDatasetOrFields() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "hny=";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
        assertThat(context.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aTrateStateHeaderWithDataset_EXPECT_datasetNoFields() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "hny=ZGF0YXNldD10ZXN0LWRhdGFzZXQ";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(0);
        assertThat(context.getDataset()).isNull();
    }

    @Test
    public void GIVEN_aTrateStateHeaderWithFields_EXPECT_fieldsNoDataset() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "hny=Zm9vPWJhcg==";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(1);
        assertThat(context.getTraceFields().get("foo")).isEqualTo("bar");
        assertThat(context.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aTrateStateHeaderDatasetandFields_EXPECT_datasetAndFieldsPopulated() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "hny=Zm9vPWJhcixkYXRhc2V0PXRlc3QtZGF0YXNldA==";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(1);
        assertThat(context.getTraceFields().get("foo")).isEqualTo("bar");
        assertThat(context.getDataset()).isNull();
    }

    @Test
    public void GIVEN_aTrateStateHeaderDatasetandFieldsWithOtherVendors_EXPECT_datasetAndFieldsPopulated() {
        final String traceParentHeader = "00-4cbc8d50f02449e887e8bc2aa8020d26-ace1ecab581fc069-01";
        final String traceStateHeader = "vendor1=123,hny=Zm9vPWJhcixkYXRhc2V0PXRlc3QtZGF0YXNldA==,vendor2=abc";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, traceParentHeader);
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, traceStateHeader);

        final PropagationContext context = codec.decode(headers);
        assertThat(context.getTraceId()).isEqualTo("4cbc8d50f02449e887e8bc2aa8020d26");
        assertThat(context.getSpanId()).isEqualTo("ace1ecab581fc069");
        assertThat(context.getTraceFields().size()).isEqualTo(1);
        assertThat(context.getTraceFields().get("foo")).isEqualTo("bar");
        assertThat(context.getDataset()).isNull();
    }

    // Encode

    @Test
    public void GIVEN_anEmptyContext_EXPECT_empty() {
        final Optional<Map<String, String>> encoded = codec.encode(PropagationContext.emptyContext());

        assertThat(encoded).isEmpty();
    }

    @Test
    public void GIVEN_aPopulatedContext_EXPECT_aValidHeaderValue() {
        String traceId = "4cbc8d50f02449e887e8bc2aa8020d26";
        String spanId = "ace1ecab581fc069";
        final Map<String, String> encoded = codec.encode(new PropagationContext(traceId, spanId, null, null)).get();

        assertThat(encoded).isEqualTo(
            Collections.singletonMap(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, String.join("-", "00", traceId, spanId, "01"))
        );
    }

    @Test
    public void GIVEN_aPopulatedContextWithDataset_EXPECT_aValidHeaderValueWithTraceState() {
        String traceId = "4cbc8d50f02449e887e8bc2aa8020d26";
        String spanId = "ace1ecab581fc069";
        String dataset = "test-dataset";

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, String.join("-", "00", traceId, spanId, "01"));

        final Map<String, String> encoded = codec.encode(new PropagationContext(traceId, spanId, dataset, null)).get();
        assertThat(encoded).isEqualTo(headers);
    }

    @Test
    public void GIVEN_aPopulatedContextWithFields_EXPECT_aValidHeaderValueWithTraceState() {
        String traceId = "4cbc8d50f02449e887e8bc2aa8020d26";
        String spanId = "ace1ecab581fc069";
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("foo", "bar");
        fields.put("one", "two");

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, String.join("-", "00", traceId, spanId, "01"));
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER, "hny=Zm9vPWJhcixvbmU9dHdv");

        final Map<String, String> encoded = codec.encode(new PropagationContext(traceId, spanId, null, fields)).get();
        assertThat(encoded).isEqualTo(headers);
    }

    @Test
    public void GIVEN_aPopulatedContextWithDatasetAndFields_EXPECT_aValidHeaderValueWithTraceState() {
        String traceId = "4cbc8d50f02449e887e8bc2aa8020d26";
        String spanId = "ace1ecab581fc069";
        String dataset = "test-dataset";
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("foo", "bar");
        fields.put("one", "two");

        final Map<String, String> headers = new HashMap<>();
        headers.put(W3CPropagationCodec.W3C_TRACEPARENT_HEADER, String.join("-", "00", traceId, spanId, "01"));
        headers.put(W3CPropagationCodec.W3C_TRACESTATE_HEADER,  "hny=Zm9vPWJhcixvbmU9dHdv");

        final Map<String, String> encoded = codec.encode(new PropagationContext(traceId, spanId, dataset, fields)).get();
        assertThat(encoded).isEqualTo(headers);
    }
}
