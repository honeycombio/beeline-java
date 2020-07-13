package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.libhoney.transport.json.JsonDeserializer;
import io.honeycomb.libhoney.transport.json.JsonSerializer;
import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class HttpHeaderV1PropagationCodecTest {

    private HttpHeaderV1PropagationCodec codec;

    @Before
    public void setUp() {
        codec = new HttpHeaderV1PropagationCodec();
    }

    @Test
    public void GIVEN_aValidTraceValue_EXPECT_toDecodeCorrectly() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;trace_id=123,parent_id=abc,dataset=hellodataset,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("abc");
        assertThat(decode.getTraceId()).isEqualTo("123");
        assertThat(decode.getDataset()).isEqualTo("hellodataset");
        assertThat(decode.getTraceFields()).containsExactly(entry("test", "value"));
    }

    @Test
    public void GIVEN_aTraceHeaderWhereKVsareInReverseOrder_EXPECT_toDecodeCorrectly() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;context=" + jsonAsBase64 + ",dataset=hellodataset,parent_id=abc,trace_id=123";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("abc");
        assertThat(decode.getTraceId()).isEqualTo("123");
        assertThat(decode.getDataset()).isEqualTo("hellodataset");
        assertThat(decode.getTraceFields()).containsExactly(entry("test", "value"));
    }

    @Test
    public void GIVEN_aValidTraceValueWithComplexJson_EXPECT_toDecodeCorrectly() {
        final String jsonString = "{\"testArray\": [1, 2, 3], \"testObject\": {\"testBoolean\": true}}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;trace_id=beef-patty,parent_id=turkey_roast,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("turkey_roast");
        assertThat(decode.getTraceId()).isEqualTo("beef-patty");
        final List testArray = (List) decode.getTraceFields().get("testArray");
        final Map testObject = (Map) decode.getTraceFields().get("testObject");
        assertThat(testArray).containsExactly(1, 2, 3);
        assertThat(testObject).containsExactly(entry("testBoolean", true));
    }

    @Test
    public void GIVEN_aValidTraceThatHasAContextOfNull_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "1;trace_id=123,parent_id=abc,context=" + null;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("abc");
        assertThat(decode.getTraceId()).isEqualTo("123");
        assertThat(decode.getTraceFields()).isEmpty();
    }

    @Test
    public void GIVEN_aValidTraceThatHasAMissingDataset_EXPECT_datasetToBeNull() {
        final String traceHeader = "1;trace_id=123,parent_id=abc";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getDataset()).isEqualTo(null);
    }

    @Test
    public void GIVEN_aValidTraceThatHasAnEncodedDataset_EXPECT_datasetToBeDecoded() {
        final String traceHeader = "1;trace_id=123,parent_id=abc,dataset=%2Fhello+world";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getDataset()).isEqualTo("/hello world");
    }

    @Test
    public void GIVEN_aTraceHeaderMissingParentID_EXPECT_emptyContext() {
        final String traceHeader = "1;trace_id=123";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderMissingTraceID_EXPECT_emptyContext() {
        final String traceHeader = "1;parent_id=123";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aTraceHeaderThePayloadPart_EXPECT_emptyContext() {
        final String traceHeader = "1;";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aValidTraceThatOmitsTraceFields_EXPECT_toDecodeCorrectly() {
        final String traceHeader = "1;trace_id=123,parent_id=abc";

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("abc");
        assertThat(decode.getTraceId()).isEqualTo("123");
        assertThat(decode.getTraceFields()).isEmpty();
    }

    @Test
    public void GIVEN_aValidTraceValue_EXPECT_JsonStringToBePassedToDeserializer() throws IOException {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;trace_id=123,parent_id=abc,context=" + jsonAsBase64;
        final JsonSerializer serializer = mock(JsonSerializer.class);
        final JsonDeserializer deserializer = mock(JsonDeserializer.class);
        codec = new HttpHeaderV1PropagationCodec(serializer, deserializer);

        codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        verify(deserializer).deserialize(jsonString.getBytes("utf-8"));
        verifyZeroInteractions(serializer);
    }

    @Test
    public void GIVEN_aTraceValueWithAnUnknownKey_EXPECT_itToBeSkipped() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;christmas_food=mince_pie,trace_id=123,parent_id=abc,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("abc");
        assertThat(decode.getTraceId()).isEqualTo("123");
        assertThat(decode.getTraceFields()).containsExactly(entry("test", "value"));
    }

    @Test
    public void GIVEN_amEmptyParameter_EXPECT_anEmptyContext() {
        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, ""));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aNullParameter_EXPECT_anEmptyContext() {
        final PropagationContext decode = codec.decode(null);

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aMissingTraceId_EXPECT_anEmptyContext() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;parent_id=abc,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_aMissingParentId_EXPECT_anEmptyContext() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String traceHeader = "1;trace_id=abc,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_anUnknownVersion_EXPECT_anEmptyContext() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        final String versionString = "2";
        final String traceHeader = versionString + ";parent_id=123,trace_id=abc,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_anInvalidFormat_EXPECT_anEmptyContext() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        // wrong separator between the version and payload:
        final String traceHeader = "1,parent_id=123,trace_id=abc,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode).isEqualTo(PropagationContext.emptyContext());
    }

    @Test
    public void GIVEN_InvalidJsonInContext_EXPECT_anEmptyContext() {
        final String jsonString = "{\"test\":\"value\"";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        // wrong separator between the version and payload:
        final String traceHeader = "1;parent_id=123,trace_id=abc,context=" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("123");
        assertThat(decode.getTraceId()).isEqualTo("abc");
        assertThat(decode.getTraceFields()).isEmpty();
    }

    @Test
    public void GIVEN_InvalidBase64StringInContext_EXPECT_anEmptyContext() {
        final String jsonString = "{\"test\":\"value\"}";
        final String jsonAsBase64 = Base64.encodeBase64String(jsonString.getBytes(UTF_8));
        // wrong separator between the version and payload:
        final String traceHeader = "1;parent_id=123,trace_id=abc,context=" + "$" + jsonAsBase64;

        final PropagationContext decode = codec.decode(Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, traceHeader));

        assertThat(decode.getSpanId()).isEqualTo("123");
        assertThat(decode.getTraceId()).isEqualTo("abc");
        assertThat(decode.getTraceFields()).isEmpty();
    }

    @Test
    public void GIVEN_anEmptyContext_EXPECT_empty() {
        final Optional<Map<String, String>> encoded = codec.encode(PropagationContext.emptyContext());

        assertThat(encoded).isEmpty();
    }

    @Test
    public void GIVEN_aMissingIds_EXPECT_empty() {
        final Optional<Map<String, String>> encoded = codec.encode(new PropagationContext("", "", "", Collections.singletonMap("key", "value")));

        assertThat(encoded).isEmpty();
    }

    @Test
    public void GIVEN_aPopulatedContext_EXPECT_aValidHeaderValue() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("abc", "123", "myDataset", Collections.singletonMap("key", "value"))).get();

        assertThat(encoded).isEqualTo(
            Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, "1;trace_id=abc,parent_id=123,dataset=myDataset,context=" + Base64.encodeBase64String("{\"key\":\"value\"}".getBytes(UTF_8)))
        );
    }

    @Test
    public void GIVEN_aContextWithADataset_EXPECT_datasetStringToBeEncoded() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("abc", "123", "/hello world", Collections.singletonMap("key", "value"))).get();

        assertThat(encoded).isEqualTo(
            Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, "1;trace_id=abc,parent_id=123,dataset=%2Fhello+world,context=" + Base64.encodeBase64String("{\"key\":\"value\"}".getBytes(UTF_8)))
        );
    }


    @Test
    public void GIVEN_aPopulatedContextWithoutADataset_EXPECT_aValidHeaderValue() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("abc", "123", null, Collections.singletonMap("key", "value"))).get();

        assertThat(encoded).isEqualTo(
            Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, "1;trace_id=abc,parent_id=123,context=" + Base64.encodeBase64String("{\"key\":\"value\"}".getBytes(UTF_8)))
        );
    }

    @Test
    public void GIVEN_aPopulatedContextWithoutAnyFields_EXPECT_aValidHeaderValue() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("abc", "123", "myDataset", Collections.emptyMap())).get();

        assertThat(encoded).isEqualTo(
            Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, "1;trace_id=abc,parent_id=123,dataset=myDataset")
        );
    }

    @Test
    public void GIVEN_aPopulatedContextWithAComplexObject_EXPECT_aValidHeaderValue() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("abc", "123", "myDataset", Collections.singletonMap("key", Collections.singletonMap("nested-key", "value")))).get();

        assertThat(encoded).isEqualTo(
            Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, "1;trace_id=abc,parent_id=123,dataset=myDataset,context=" + Base64.encodeBase64String("{\"key\":{\"nested-key\":\"value\"}}".getBytes(UTF_8)))
        );
    }

    @Test
    public void GIVEN_aPopulatedContextWithAnUnserializableObject_EXPECT_aValidHeaderValueOmittingContext() {
        final Map<String, String> encoded = codec.encode(new PropagationContext("abc", "123", "myDataset", Collections.singletonMap("key", Collections.singletonMap("nested-key", new Unserializable())))).get();

        assertThat(encoded).isEqualTo(
            Map.of(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, "1;trace_id=abc,parent_id=123,dataset=myDataset")
        );
    }

    public static class Unserializable {
        private String field = "value";
    }
}
