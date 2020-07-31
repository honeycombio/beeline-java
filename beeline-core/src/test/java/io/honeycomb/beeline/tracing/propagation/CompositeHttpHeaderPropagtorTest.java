package io.honeycomb.beeline.tracing.propagation;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

public class CompositeHttpHeaderPropagtorTest {

    @Test(expected = IllegalArgumentException.class)
    public void GIVEN_nullCodecsList_EXPECT_illegalArgumentException() {
        new CompositeHttpHeaderPropagtor(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void GIVEN_emptyCodecsList_EXPECT_illegalArgumentException() {
        new CompositeHttpHeaderPropagtor(Collections.emptyList());
    }

    // DECODE

    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_codecList_EXPECT_returnsContextFromCodec() {

        PropagationContext mockContext = mock(PropagationContext.class);
        PropagationCodec<Map<String, String>> mockCodec = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec.decode(Mockito.anyMap())).thenReturn(mockContext);

        CompositeHttpHeaderPropagtor propagator = new CompositeHttpHeaderPropagtor(Collections.singletonList(mockCodec));
        final PropagationContext context = propagator.decode(Mockito.anyMap());

        assertSame(mockContext, context);
        verify(mockCodec, times(1)).decode(Mockito.anyMap());
        verifyNoMoreInteractions(mockCodec);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_listOfCodecs_EXPECT_tryEachOnceUntilSuccess() {

        // first one fails (returns empty context)
        PropagationCodec<Map<String, String>> mockCodec1 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec1.decode(anyMap())).thenReturn(PropagationContext.emptyContext());

        // second one succeeds (returns non-empty)
        PropagationContext mockContext = mock(PropagationContext.class);
        PropagationCodec<Map<String, String>> mockCodec2 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec2.decode(anyMap())).thenReturn(mockContext);

        // third codec is not called (codec 2 succeeded)
        PropagationCodec<Map<String, String>> mockCodec3 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec3.decode(anyMap())).thenReturn(PropagationContext.emptyContext());

        CompositeHttpHeaderPropagtor propagtor = new CompositeHttpHeaderPropagtor(Arrays.asList(mockCodec1, mockCodec2, mockCodec3));
        PropagationContext context = propagtor.decode(Mockito.anyMap());

        assertSame(mockContext, context);
        verify(mockCodec1, times(1)).decode(Mockito.anyMap());
        verify(mockCodec2, times(1)).decode(Mockito.anyMap());
        verify(mockCodec3, times(0)).decode(Mockito.anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_listOfCodecsAndNoneSucceed_EXPECT_emptyContext() {

        PropagationCodec<Map<String, String>> mockCodec1 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec1.decode(anyMap())).thenReturn(PropagationContext.emptyContext());

        CompositeHttpHeaderPropagtor propagtor = new CompositeHttpHeaderPropagtor(Collections.singletonList(mockCodec1));
        PropagationContext context = propagtor.decode(Mockito.anyMap());

        assertSame(PropagationContext.emptyContext(), context);
        verify(mockCodec1, times(1)).decode(Mockito.anyMap());
    }

    // ENCODE

    // can encode with one codec
    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_singleCodec_EXPECT_encodeReturnsValidHeaders() {

        Optional<Map<String, String>> codecHeaders = Optional.of(Collections.singletonMap("codec1", "value"));
        PropagationCodec<Map<String, String>> mockCodec1 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec1.encode(any(PropagationContext.class))).thenReturn(codecHeaders);

        PropagationContext mockContext = new PropagationContext(null, null, null, null);
        CompositeHttpHeaderPropagtor propagtor = new CompositeHttpHeaderPropagtor(Collections.singletonList(mockCodec1));
        Optional<Map<String, String>> headers = propagtor.encode(mockContext);

        assertNotSame(codecHeaders, headers);
        assertTrue(headers.get().get("codec1") == "value");
        verify(mockCodec1, times(1)).encode(mockContext);
    }

    // can encode with multiple codecs
    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_multipleCodecs_EXPECT_encodeReturnsValidHeadersFromAllCodecs() {

        Optional<Map<String, String>> codec1Headers = Optional.of(Collections.singletonMap("codec1", "value1"));
        PropagationCodec<Map<String, String>> mockCodec1 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec1.encode(any(PropagationContext.class))).thenReturn(codec1Headers);

        Optional<Map<String, String>> codec2Headers = Optional.of(Collections.singletonMap("codec2", "value2"));
        PropagationCodec<Map<String, String>> mockCodec2 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec2.encode(any(PropagationContext.class))).thenReturn(codec2Headers);

        PropagationContext mockContext = new PropagationContext(null, null, null, null);
        CompositeHttpHeaderPropagtor propagtor = new CompositeHttpHeaderPropagtor(Arrays.asList(mockCodec1, mockCodec2));
        Optional<Map<String, String>> headers = propagtor.encode(mockContext);

        assertNotSame(codec2Headers, headers);
        assertNotSame(codec2Headers, headers);
        verify(mockCodec1, times(1)).encode(mockContext);
        verify(mockCodec2, times(1)).encode(mockContext);
        assertTrue(headers.get().get("codec1") == "value1");
        assertTrue(headers.get().get("codec2") == "value2");
    }

    // ignores empty optional encodes
    @Test
    @SuppressWarnings("unchecked")
    public void GIVEN_multipleCodecsWhenOneReturnsEmptyOptional_EXPECT_emptyOptionalIsIgnored() {

        PropagationCodec<Map<String, String>> mockCodec1 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec1.encode(any(PropagationContext.class))).thenReturn(Optional.empty());

        Optional<Map<String, String>> codec2Headers = Optional.of(Collections.singletonMap("codec2", "value2"));
        PropagationCodec<Map<String, String>> mockCodec2 = (PropagationCodec<Map<String, String>>) mock(PropagationCodec.class);
        when(mockCodec2.encode(any(PropagationContext.class))).thenReturn(codec2Headers);

        PropagationContext mockContext = new PropagationContext(null, null, null, null);
        CompositeHttpHeaderPropagtor propagtor = new CompositeHttpHeaderPropagtor(Arrays.asList(mockCodec1, mockCodec2));
        Optional<Map<String, String>> headers = propagtor.encode(mockContext);

        assertNotSame(codec2Headers, headers);
        assertNotSame(codec2Headers, headers);
        verify(mockCodec1, times(1)).encode(mockContext);
        verify(mockCodec2, times(1)).encode(mockContext);
        assertTrue(headers.get().get("codec2") == "value2");
    }
}
