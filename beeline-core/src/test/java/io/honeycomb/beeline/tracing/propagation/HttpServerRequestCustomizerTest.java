package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.libhoney.shaded.org.apache.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static io.honeycomb.beeline.tracing.propagation.MockSpanUtils.stubFluentCalls;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerRequestCustomizerTest {

    private static final String EXPECTED_PROTOCOL = "expectedProtocol";
    private final String EXPECTED_METHOD = "expectedMethod";
    private final String EXPECTED_REMOTE_ADDRESS = "expectedRemoteAddress";

    @Mock
    private HttpServerRequestAdapter mockHttpRequest;
    @Mock
    private Span mockSpan;

    private HttpServerRequestSpanCustomizer httpServerRequestSpanCustomizer;

    @Before
    public void setup() {
        stubFluentCalls(mockSpan);
        httpServerRequestSpanCustomizer = new HttpServerRequestSpanCustomizer();
    }

    @Test
    public void testOnlyRequiredSpanFieldsAreAdded() {
        stubRequiredRequestData(mockHttpRequest);

        httpServerRequestSpanCustomizer.customize(mockSpan, mockHttpRequest);

        verify(mockSpan, times(6)).addField(anyString(), any());
        verifyRequiredSpanData(mockSpan, true);
    }

    @Test
    public void testOptionalSpanFieldsAreAddedWhenDataIsPresent() {
        final String expectedContentType = "expectedContentType";
        final String expectedAccept = "expectedAccept";
        final String expectedUserAgent = "expectedUserAgent";
        final String expectedFP = "expectedXFP";
        final String expectedXFF = "expectedXFF";
        final String expectedHost = "expectedHost";
        final String expectedPath = "expectedPath";
        final String expectedScheme = "expectedScheme";
        final Map<String, List<String>> expectedQueryParams = new HashMap<>();
        final List<String> queryParamValues = Collections.singletonList("a value");
        expectedQueryParams.put("param1", queryParamValues);
        final int expectedContentLength = 10;
        when(mockHttpRequest.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(Optional.of(expectedContentType));
        when(mockHttpRequest.getFirstHeader(HttpHeaders.ACCEPT)).thenReturn(Optional.of(expectedAccept));
        when(mockHttpRequest.getFirstHeader(HttpHeaders.USER_AGENT)).thenReturn(Optional.of(expectedUserAgent));
        when(mockHttpRequest.getFirstHeader("x-forwarded-proto")).thenReturn(Optional.of(expectedFP));
        when(mockHttpRequest.getFirstHeader("x-forwarded-for")).thenReturn(Optional.of(expectedXFF));
        when(mockHttpRequest.getHost()).thenReturn(Optional.of(expectedHost));
        when(mockHttpRequest.getPath()).thenReturn(Optional.of(expectedPath));
        when(mockHttpRequest.getScheme()).thenReturn(Optional.of(expectedScheme));
        when(mockHttpRequest.getQueryParams()).thenReturn(expectedQueryParams);
        when(mockHttpRequest.getContentLength()).thenReturn(expectedContentLength);

        stubRequiredRequestData(mockHttpRequest);

        httpServerRequestSpanCustomizer.customize(mockSpan, mockHttpRequest);

        verify(mockSpan, times(16)).addField(anyString(), any());
        verifyRequiredSpanData(mockSpan, true);
        verify(mockSpan).addField(REQUEST_CONTENT_LENGTH_FIELD, expectedContentLength);
        verify(mockSpan).addField(REQUEST_CONTENT_TYPE_FIELD, expectedContentType);
        verify(mockSpan).addField(REQUEST_ACCEPT_FIELD, expectedAccept);
        verify(mockSpan).addField(USER_AGENT_FIELD, expectedUserAgent);
        verify(mockSpan).addField(FORWARD_FOR_HEADER_FIELD, expectedXFF);
        verify(mockSpan).addField(FORWARD_PROTO_HEADER_FIELD, expectedFP);
        verify(mockSpan).addField(REQUEST_HOST_FIELD, expectedHost);
        verify(mockSpan).addField(REQUEST_PATH_FIELD, expectedPath);
        verify(mockSpan).addField(REQUEST_SCHEME_FIELD, expectedScheme);
        verify(mockSpan).addField(REQUEST_QUERY_PARAMS_FIELD, expectedQueryParams);
    }

    @Test
    public void testNonAjaxXRequestWithHeader() {
        stubRequiredRequestData(mockHttpRequest);
        when(mockHttpRequest.getFirstHeader("x-requested-with")).thenReturn(Optional.of("NonAjax"));

        httpServerRequestSpanCustomizer.customize(mockSpan, mockHttpRequest);

        verify(mockSpan, times(6)).addField(anyString(), any());
        verifyRequiredSpanData(mockSpan, false);
    }

    private void stubRequiredRequestData(final HttpServerRequestAdapter mockHttpRequest) {
        when(mockHttpRequest.getMethod()).thenReturn(EXPECTED_METHOD);
        when(mockHttpRequest.getHttpVersion()).thenReturn(EXPECTED_PROTOCOL);
        when(mockHttpRequest.isSecure()).thenReturn(true);
        when(mockHttpRequest.getRemoteAddress()).thenReturn(EXPECTED_REMOTE_ADDRESS);
        when(mockHttpRequest.getFirstHeader("x-requested-with")).thenReturn(Optional.of("XMLHttpRequest"));
    }

    private void verifyRequiredSpanData(final Span mockSpan, final boolean ajaxValue) {
        verify(mockSpan).addField(TYPE_FIELD, HttpServerPropagator.OPERATION_TYPE_HTTP_SERVER);
        verify(mockSpan).addField(REQUEST_METHOD_FIELD, EXPECTED_METHOD);
        verify(mockSpan).addField(REQUEST_HTTP_VERSION_FIELD, EXPECTED_PROTOCOL);
        verify(mockSpan).addField(REQUEST_SECURE_FIELD, true);
        verify(mockSpan).addField(REQUEST_AJAX_FIELD, ajaxValue);
        verify(mockSpan).addField(REQUEST_REMOTE_ADDRESS_FIELD, EXPECTED_REMOTE_ADDRESS);
    }
}
