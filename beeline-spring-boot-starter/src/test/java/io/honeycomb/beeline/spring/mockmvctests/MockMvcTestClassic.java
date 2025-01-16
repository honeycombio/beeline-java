package io.honeycomb.beeline.spring.mockmvctests;

import io.honeycomb.beeline.spring.autoconfig.BeelineAutoconfig;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.NestedServletException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.META_SENT_BY_PARENT_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_ERROR_DETAIL_FIELD;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.REQUEST_ERROR_FIELD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * These are mockmvc tests which simulate Spring MVC's web server environment. They are faster than fuller
 * integration tests like {@link io.honeycomb.beeline.spring.e2e.End2EndTest}, but also have some limitations as the
 * environment is mocked and doesn't always behave like the real thing.
 * <p>
 * The web server endpoints that are hit with the simulated requests are defined in {@link MockMvcTestController}.
 * They are generally simple stubs to verify our implementation against a specific behaviour of Spring MVC framework.
 */
@SuppressWarnings({"unchecked", "SpringJavaInjectionPointsAutowiringInspection"})
@RunWith(SpringRunner.class)
@WebMvcTest
@ImportAutoConfiguration({BeelineAutoconfig.class, AopAutoConfiguration.class})
@ActiveProfiles("request-test-classic") // means application-request-test-classic.properties is picked up
public class MockMvcTestClassic {

    @Configuration
    static class TestConfig {
        @Bean
        MockMvcTestController controller() {
            return new MockMvcTestController();
        }
    }

    @Autowired
    private Tracer tracer;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private HoneyClient client;
    private ArgumentCaptor<ResolvedEvent> eventCaptor = ArgumentCaptor.forClass(ResolvedEvent.class);

    // replace the transport from the BeelineAutoconfig with mock, so we can capture and inspect events to be sent to honeycomb
    @MockBean
    private Transport transport;

    private void setTransportStubs() {
        when(transport.submit(any(ResolvedEvent.class))).thenReturn(true);
        when(transport.getResponseObservable()).thenReturn(mock(ResponseObservable.class));
    }

    @Before
    public void setup() {
        setTransportStubs();
    }

    private String[] problemFields = {
        REQUEST_ERROR_FIELD,
        REQUEST_ERROR_DETAIL_FIELD,
        META_SENT_BY_PARENT_FIELD,
    };

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithRequiredApiAttributes() throws Exception {
        mvc.perform(get("/basic-get")).andReturn().getResponse().getContentAsString();

        verify(transport).submit(eventCaptor.capture());
        final ResolvedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getDataset()).isEqualTo("testDataset");
        assertThat(capturedEvent.getWriteKey()).isEqualTo("e38be416d0d68f9ed1e96432ac1a3380");
        assertThat(capturedEvent.getApiHost().toString()).isEqualTo("http://localhost:8089");
    }

    @Test
    public void WHEN_makingARequestWithHoneycombHeaderSpecifyingADataset_EXPECT_AllEventsToIgnoreDatasetValueAndUseConfiguredDataset() throws Exception {
        final PropagationContext context = new PropagationContext("current-trace-1", "parent-span-1", "myDataset", Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> headers = Propagation.honeycombHeaderV1().encode(context).get();

        final MockHttpServletRequestBuilder builder = get("/annotation-span")
            .header("x-application-header", "fish");
        headers.forEach((k,v) -> builder.header(k, v));
        mvc.perform(builder);

        final List<ResolvedEvent> eventFields = captureNoOfEvents(2);
        final ResolvedEvent aopSpan = eventFields.get(0);
        final ResolvedEvent requestSpan = eventFields.get(1);

        assertThat(aopSpan.getDataset()).isEqualTo("testDataset");
        assertThat(requestSpan.getDataset()).isEqualTo("testDataset");
    }

    @Test
    public void WHEN_makingARequestWithHoneycombHeaderNotSpecifyingAnyDataset_EXPECT_AllEventsToSendToConfiguredDataset() throws Exception {
        final PropagationContext context = new PropagationContext("current-trace-1", "parent-span-1", null, Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> headers = Propagation.honeycombHeaderV1().encode(context).get();

        final MockHttpServletRequestBuilder builder = get("/annotation-span")
            .header("x-application-header", "fish");
        headers.forEach((k,v) -> builder.header(k, v));
        mvc.perform(builder);

        final List<ResolvedEvent> eventFields = captureNoOfEvents(2);
        final ResolvedEvent aopSpan = eventFields.get(0);
        final ResolvedEvent requestSpan = eventFields.get(1);

        assertThat(aopSpan.getDataset()).isEqualTo("testDataset");
        assertThat(requestSpan.getDataset()).isEqualTo("testDataset");
    }

    private List<ResolvedEvent> captureNoOfEvents(final int times) {
        Mockito.verify(transport, Mockito.times(times)).submit(eventCaptor.capture());
        return eventCaptor.getAllValues();
    }

}
