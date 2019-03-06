package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.spring.autoconfig.BeelineAutoconfig;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Integration test case to ensure that the interceptor is configured correctly with the Beeline auto configuration.
 * Unit tests are in {@link BeelineRestTemplateInterceptorTest}.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@RunWith(SpringRunner.class)
@SpringBootTest
@ImportAutoConfiguration({RestTemplateAutoConfiguration.class, BeelineAutoconfig.class})
@ActiveProfiles("request-test") // means application-request-test.properties is picked up
public class BeelineRestTemplateInterceptorIntegrationTest {

    @Configuration
    public static class Config {
        // empty since spring boot test needs at least one configuration class
    }

    @Autowired
    private RestTemplateBuilder builder;

    @Autowired
    private BeelineRestTemplateInterceptor interceptor;

    @Autowired
    private RestTemplateCustomizer customizer;

    @Autowired
    private Beeline beeline;

    @MockBean
    private Transport transport;

    private RestTemplate template;
    private MockRestServiceServer server;
    private ArgumentCaptor<ResolvedEvent> eventCaptor = ArgumentCaptor.forClass(ResolvedEvent.class);

    @Before
    public void setUp() {
        mockTransport();
        template = builder.build();
        server = MockRestServiceServer.bindTo(template).build();
    }

    private Transport mockTransport() {
        when(transport.submit(any(ResolvedEvent.class))).thenReturn(true);
        when(transport.getResponseObservable()).thenReturn(mock(ResponseObservable.class));
        return transport;
    }

    private List<ResolvedEvent> captureNoOfEvents(final int times) {
        verify(transport, times(times)).submit(eventCaptor.capture());
        return eventCaptor.getAllValues();
    }

    @Test
    public void GIVEN_beelineAndRestTemplateBuilderAreAutoConfigured_WHEN_buildingRestTemplate_EXPECT_thatItContainsTheInterceptor() {
        assertThat(template.getInterceptors()).contains(interceptor);
    }

    @Test
    public void GIVEN_aRestTemplateMock_WHEN_restTemplateIsCustomisedWithOurInteceptor_EXPECT_restTemplateToContainOurInterceptor() {
        final RestTemplate mock = mock(RestTemplate.class);
        final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        when(mock.getInterceptors()).thenReturn(interceptors);

        interceptor.customizer().customize(mock);

        verify(mock).getInterceptors();
        assertThat(interceptors).contains(interceptor);
    }

    @Test
    public void GIVEN_aRestTemplateMock_WHEN_restTemplateIsCustomisedWithOurCustomizer_EXPECT_restTemplateToContainOurInterceptor() {
        final RestTemplate mock = mock(RestTemplate.class);
        final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        when(mock.getInterceptors()).thenReturn(interceptors);

        customizer.customize(mock);

        verify(mock).getInterceptors();
        assertThat(interceptors).contains(interceptor);
    }

    @Test
    public void GIVEN_noActiveSpan_WHEN_makingRequestWithInstrumentedTemplate_EXPECT_noSpansToBeSubmitted() {
        server
            .expect(ExpectedCount.once(), requestTo("/span-test"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("Success!", MediaType.TEXT_PLAIN));

        final String response = template.getForObject("/span-test", String.class);

        assertThat(response).isEqualTo("Success!");
        verifyZeroInteractions(transport);
        server.verify();
    }

    @Test
    public void GIVEN_anActiveSpan_WHEN_makingRequestWithInstrumentedTemplate_EXPECT_spansToBeSubmitted() {
        server
            .expect(ExpectedCount.once(), requestTo("/span-test"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.ACCEPTED).body("Accepted!").contentType(MediaType.TEXT_PLAIN));

        final Span root = beeline.getTracer().startTrace(
            beeline.getSpanBuilderFactory().createBuilder().setSpanName("span").setServiceName("service").build()
        );
        template.getForObject("/span-test", String.class);
        root.close();

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent clientEvent = resolvedEvents.get(0);
        final ResolvedEvent rootEvent = resolvedEvents.get(1);
        assertThat(clientEvent.getFields())
            .contains(
                entry("name", "http_client_request"),
                entry("service_name", "service"),
                entry("type", "http_client"),
                entry("client.request.path", "/span-test"),
                entry("client.request.method", "GET"),
                entry("client.response.status_code", 202));
        assertThat(rootEvent.getFields())
            .contains(
                entry("name", "span"),
                entry("service_name", "service"));
        server.verify();
    }

    @Test
    public void GIVEN_anActiveSpan_WHEN_makingRequestWithInstrumentedTemplate_EXPECT_spansToBeSubmitted1() {
        server
            .expect(ExpectedCount.once(), requestTo("/span-test"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withServerError());

        final Span root = beeline.getTracer().startTrace(
            beeline.getSpanBuilderFactory().createBuilder().setSpanName("span").setServiceName("service").build()
        );
        assertThatThrownBy(() -> template.getForObject("/span-test", String.class)).isInstanceOf(RestClientException.class);
        root.close();

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent clientEvent = resolvedEvents.get(0);
        final ResolvedEvent rootEvent = resolvedEvents.get(1);
        assertThat(clientEvent.getFields())
            .contains(
                entry("name", "http_client_request"),
                entry("service_name", "service"),
                entry("type", "http_client"),
                entry("client.request.path", "/span-test"),
                entry("client.request.method", "GET"),
                entry("client.response.status_code", 500));
        assertThat(rootEvent.getFields())
            .contains(
                entry("name", "span"),
                entry("service_name", "service"));
        server.verify();
    }

}
