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
@ActiveProfiles("request-test") // means application-request-test.properties is picked up
public class MockMvcTest {

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

    private void checkMetaFields(final Map<String, Object> eventFields) {
        // Can't verify beeline_version without breaking test in Intellij as it doesn't seem to generate
        // the necessary manifest:
        // assertThat(eventFields).containsEntry("meta.beeline_version", "0.0.1");
        assertThat(eventFields).containsKey("meta.beeline_version");
        assertThat(eventFields).containsEntry("meta.package", "Spring Boot");
        assertThat(eventFields).containsKey("meta.package_version");
        assertThat((Iterable<String>) eventFields.get("meta.instrumentations")).containsExactlyInAnyOrder("spring_mvc", "spring_aop", "spring_rest_template", "spring_jdbc");
        assertThat(eventFields).containsEntry("meta.instrumentation_count", 4);
        assertThat(eventFields).containsEntry("meta.local_hostname", tryGetLocalHostname());
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithResponseFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("response.header.content_type", "text/plain;charset=UTF-8");
        assertThat(eventFields).containsEntry("response.status_code", 200);
    }

    @Test
    public void WHEN_GETingWithAcceptHeader_EXPECT_spanToBeSubmittedWithAcceptHeaderField() throws Exception {
        mvc.perform(get("/basic-get").header(HttpHeaders.ACCEPT, "text/plain"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.header.accept", "text/plain");
    }

    @Test
    public void WHEN_GETing_EXPECT_handlerMappingFieldsToBeIncluded() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.path", "/basic-get");
        assertThat(eventFields).containsEntry("spring.request.matched_pattern", "/basic-get");
        assertThat(eventFields).containsEntry("spring.request.handler_type", "HandlerMethod");
        assertThat(eventFields).containsEntry("spring.request.handler_method", "MockMvcTestController#basicGet");
        assertThat(eventFields).containsEntry("name", "BasicGet");
        assertThat(eventFields).doesNotContainKey("request.query");
    }

    @Test
    public void WHEN_GETingWithQueryParams_EXPECT_FieldWithMapOfQueryParams() throws Exception {
        mvc.perform(get("/basic-get?first-word=hola&second-word=mundo&second-word=welt"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.path", "/basic-get");
        assertThat(eventFields).containsEntry("name", "BasicGetWithQueryParams");
        assertThat(eventFields).containsEntry("spring.request.handler_method", "MockMvcTestController#basicGetWithQueryParams");
        assertThat(eventFields).containsEntry("spring.request.matched_pattern", "/basic-get");
        assertThat((Map<String, List<String>>) eventFields.get("request.query")).containsAllEntriesOf(
            of("first-word", Collections.singletonList("hola"), "second-word", Arrays.asList("mundo", "welt")));
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithCorrectMetaFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithoutErrorFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).doesNotContainKeys(problemFields);
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithIDFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsKeys("trace.span_id", "trace.trace_id");
        assertThat(eventFields).doesNotContainKeys("trace.parent_id");
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithRequiredApiAttributes() throws Exception {
        mvc.perform(get("/basic-get")).andReturn().getResponse().getContentAsString();

        verify(transport).submit(eventCaptor.capture());
        final ResolvedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getDataset()).isEqualTo("testDataset");
        assertThat(capturedEvent.getWriteKey()).isEqualTo("testWriteKey");
        assertThat(capturedEvent.getApiHost().toString()).isEqualTo("http://localhost:8089");
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithBasicFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("service_name", "IntegrationTestApp");
        assertThat(eventFields).containsEntry("name", "BasicGet");
        assertThat(eventFields).containsEntry("type", "http_server");
        assertThat((double) eventFields.get("duration_ms")).isGreaterThan(0);
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithoutInapplicableFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).doesNotContainKeys(
            "request.spring.async_dispatch",
            "request.header.content_type",
            "request.header.x_forwarded_for",
            "request.header.x_forwarded_proto");
    }

    @Test
    public void WHEN_GETing_and_AFieldAddedWithinUserCode_EXPECT_spanToBeSubmittedWithUsersField() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("user-field", "user-data");
    }

    @Test
    public void WHEN_GETing_EXPECT_spanToBeSubmittedWithVariousRequestFields() throws Exception {
        mvc.perform(get("/basic-get"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).
            contains(
                entry("request.host", "localhost"),
                entry("request.method", "GET"),
                entry("request.http_version", "HTTP/1.1"),
                entry("request.path", "/basic-get"),
                entry("request.scheme", "http"),
                entry("request.secure", false),
                entry("request.xhr", false)
            )
            .doesNotContainKey("request.content_length");
    }

    @Test
    public void WHEN_GETing_and_AdditionalPathComponents_EXPECT_spanToBeSubmittedWithOnlyBasePathOfRequest() throws Exception {
        mvc.perform(get("/basic-get?thiswillbeignore=true#baggage").header(HttpHeaders.USER_AGENT, "Junit Test"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.path", "/basic-get");
    }

    @Test
    public void WHEN_GETing_and_AUserAgentHeader_EXPECT_spanToBeSubmittedWithUserAgent() throws Exception {
        mvc.perform(get("/basic-get").header(HttpHeaders.USER_AGENT, "Junit Test"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.header.user_agent", "Junit Test");
    }

    @Test
    public void WHEN_GETing_and_AjaxHeader_EXPECT_spanToBeSubmittedWithAjaxFieldSetToTrue() throws Exception {
        mvc.perform(get("/basic-get").header("x-requested-with", "xmlhttprequest"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.xhr", true);
    }

    @Test
    public void WHEN_GETing_and_ForwardingHeaders_EXPECT_spanToBeSubmittedWithForwardingFields() throws Exception {
        mvc.perform(get("/basic-get").header("x-forwarded-for", "Junit Test").header("x-forwarded-proto", "https"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("request.header.x_forwarded_for", "Junit Test");
        assertThat(eventFields).containsEntry("request.header.x_forwarded_proto", "https");
    }

    @Test
    public void WHEN_GETing_EXPECT_tracingContextToBeCleared() throws Exception {
        mvc.perform(get("/basic-get"));

        assertThat(tracer.getActiveSpan().isNoop()).isTrue();
        final Map<String, Object> eventFields = captureEventData();
        checkMetaFields(eventFields);
    }

    @Test
    public void WHEN_POSTing_EXPECT_relevantFieldsToBePresent() throws Exception {
        mvc.perform(post("/basic-post").contentType(MediaType.TEXT_PLAIN).content("world"))
            .andExpect(content().string("hello: world"));

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("name", "BasicPost");
        assertThat(eventFields).containsEntry("request.header.content_type", "text/plain");
        assertThat(eventFields).containsEntry("request.content_length", 5);
        assertThat(eventFields).containsEntry("response.status_code", 200);
        assertThat(eventFields).doesNotContainKeys(problemFields);
    }

    @Test
    public void GIVEN_anUnhandledExceptionIsThrow_EXPECT_errorDetailsToBeIncluded() {
        assertThatThrownBy(() -> mvc.perform(get("/throw-unhandled"))).isInstanceOf(NestedServletException.class);

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("request.error", "NestedServletException");
        assertThat(eventFields).containsKey("request.error_detail");
    }

    @Test
    public void GIVEN_aSyncHandler_WHEN_MakingRequest_EXPECT_servletRequestToBeSyncDispatch() throws Exception {
        mvc.perform(get("/basic-get"))
            .andExpect(request().asyncNotStarted())
            .andExpect(content().string("hello"));

        verify(transport).submit(any(ResolvedEvent.class));
    }

    @Test
    public void GIVEN_anAsyncHandler_WHEN_MakingRequest_EXPECT_servletRequestToBeAsyncDispatch() throws Exception {
        final MvcResult asyncResult = mvc.perform(get("/basic-get-async"))
            .andExpect(request().asyncStarted())
            .andExpect(request().asyncResult(notNullValue()))
            .andReturn();

        verifyNoMoreInteractions(transport);

        mvc.perform(asyncDispatch(asyncResult)).andExpect(content().string("hello"));

        final Map<String, Object> eventFields = captureEventData();
        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("user-field", "user-data");
        assertThat(eventFields).doesNotContainKeys(problemFields);
        assertThat(eventFields).containsEntry("spring.request.dispatcher_type", "REQUEST");
        assertThat(eventFields).containsEntry("spring.request.async_dispatch", true);
        assertThat(eventFields).containsEntry("response.status_code", 200);
    }

    @Test
    public void GIVEN_anAsyncHandlerWithCallableReturnValue_WHEN_MakingRequest_EXPECT_servletRequestToBeAsyncDispatch() throws Exception {
        final MvcResult asyncResult = mvc.perform(get("/get-async-callable"))
            .andExpect(request().asyncStarted())
            .andExpect(request().asyncResult(notNullValue()))
            .andReturn();

        verifyNoMoreInteractions(transport);

        mvc.perform(asyncDispatch(asyncResult)).andExpect(content().string("hello"));

        final Map<String, Object> eventFields = captureEventData();
        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("user-field", "user-data");
        assertThat(eventFields).doesNotContainKeys(problemFields);
        assertThat(eventFields).containsEntry("spring.request.dispatcher_type", "REQUEST");
        assertThat(eventFields).containsEntry("spring.request.async_dispatch", true);
        assertThat(eventFields).containsEntry("response.status_code", 200);
    }

    @Test
    public void GIVEN_anAsyncHandlerWithDeferredReturnValue_WHEN_MakingRequest_EXPECT_servletRequestToBeAsyncDispatch() throws Exception {
        final MvcResult asyncResult = mvc.perform(get("/get-async-deferred"))
            .andExpect(request().asyncStarted())
            .andExpect(request().asyncResult(notNullValue()))
            .andReturn();

        verifyNoMoreInteractions(transport);

        mvc.perform(asyncDispatch(asyncResult)).andExpect(content().string("hello"));

        final Map<String, Object> eventFields = captureEventData();
        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("user-field", "user-data");
        assertThat(eventFields).doesNotContainKeys(problemFields);
        assertThat(eventFields).containsEntry("spring.request.dispatcher_type", "REQUEST");
        assertThat(eventFields).containsEntry("spring.request.async_dispatch", true);
        assertThat(eventFields).containsEntry("response.status_code", 200);
    }

    @Test
    public void GIVEN_AsyncDispatch_WHEN_inspectingActiveSpanAfterEveryDispatch_EXPECT_tracerToBeCleared() throws Exception {
        final MvcResult asyncResult = mvc.perform(get("/basic-get-async")).andReturn();
        assertThat(tracer.getActiveSpan().isNoop()).isTrue();

        mvc.perform(asyncDispatch(asyncResult));
        assertThat(tracer.getActiveSpan().isNoop()).isTrue();

        final Map<String, Object> eventFields = captureEventData();
        assertThat(eventFields.get("spring.request.async_dispatch")).isEqualTo(true);
    }

    //    @Test
//    @Ignore("Appears that the mockmvc servlet doesn't invoke the async callback when exception is thrown but tomcat does")
    public void GIVEN_AsyncDispatch_WHEN_anExceptionIsThrown_EXPECT_spanToIncludeErrorDetail() throws Exception {
        final MvcResult asyncResult = mvc.perform(get("/basic-get-async-throws"))
            .andExpect(request().asyncStarted())
            .andExpect(request().asyncResult(notNullValue()))
            .andReturn();

        verifyNoMoreInteractions(transport);

        // This behaviour of having to catch the exception is specific to the mockmvc environment
        // - on a real server (like tomcat) this would turn into an unhandled 500 error
        assertThatThrownBy(
            () -> mvc.perform(asyncDispatch(asyncResult)).andExpect(content().string("hello")))
            .isInstanceOf(NestedServletException.class);

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("request.error", "NestedServletException");
        assertThat(eventFields).containsEntry("user-field", "user-data");
        assertThat(eventFields).containsKey("request.error_detail");
        assertThat(eventFields).containsEntry("spring.request.dispatcher_type", "REQUEST");
        assertThat(eventFields).containsEntry("spring.request.async_dispatch", true);
    }

    @Test
    public void GIVEN_syncDispatch_WHEN_anExceptionIsThrown_EXPECT_spanToIncludeErrorDetail() throws Exception {
        mvc.perform(get("/throw-handled")).andExpect(status().isUnauthorized());

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("test-exception-message", "Oops!");
        assertThat(eventFields).doesNotContainKeys(problemFields);
        assertThat(eventFields).containsEntry("response.status_code", 401);
    }

    @Test
    public void GIVEN_anUnmatchedPath_EXPECT_spanToIncludeFields() throws Exception {
        mvc.perform(get("/path-does-not-exist")).andExpect(status().isNotFound());

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).doesNotContainKeys(problemFields);
        assertThat(eventFields).containsEntry("spring.request.handler_type", "ResourceHttpRequestHandler");
        assertThat(eventFields).containsEntry("name", "http_get");
        assertThat(eventFields).containsEntry("response.status_code", 404);
    }

    @Test
    public void GIVEN_handlerWithPathVariables_EXPECT_matchedPatternFieldToBeIncludedInSpan() throws Exception {
        mvc.perform(get("/get-with-var/variable123/etc"));

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("user-field", "variable123");
        assertThat(eventFields).containsEntry("spring.request.matched_pattern", "/get-with-var/{pathVar}/etc");
        assertThat(eventFields).containsEntry("request.path", "/get-with-var/variable123/etc");
        assertThat(eventFields).containsEntry("spring.request.handler_type", "HandlerMethod");
        assertThat(eventFields).containsEntry("spring.request.handler_method", "MockMvcTestController#pathVar");
    }


    @Test
    public void GIVEN_handlerWithPathPattern_EXPECT_matchedPatternFieldToBeIncludedInSpan() throws Exception {
        mvc.perform(get("/prefix/suffix"));

        final Map<String, Object> eventFields = captureEventData();

        checkMetaFields(eventFields);
        assertThat(eventFields).containsEntry("request.path", "/prefix/suffix");
        assertThat(eventFields).containsEntry("spring.request.matched_pattern", "/prefix/**");
        assertThat(eventFields).containsEntry("spring.request.handler_type", "HandlerMethod");
        assertThat(eventFields).containsEntry("spring.request.handler_method", "MockMvcTestController#prefix");
    }

    @Test
    public void GIVEN_aTraceHeaderInRequest_WHEN_serverReceivesRequest_EXPECT_traceToBeContinued() throws Exception {
        final PropagationContext context = new PropagationContext("current-trace-1", "parent-span-1", null, null);
        final Map<String, String> headers = Propagation.honeycombHeaderV1().encode(context).get();

        final MockHttpServletRequestBuilder builder = get("/basic-get");
        headers.forEach((k,v) -> builder.header(k, v));
        mvc.perform(builder);
        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("trace.parent_id", "parent-span-1");
        assertThat(eventFields).containsEntry("trace.trace_id", "current-trace-1");
        assertThat(eventFields).containsEntry("response.status_code", 200);
    }

    @Test
    public void GIVEN_aTraceHeaderInRequestContainingTraceFields_WHEN_serverReceivesRequest_EXPECT_traceToBeContinuedWithTraceFields() throws Exception {
        final PropagationContext context = new PropagationContext("current-trace-1", "parent-span-1", null, Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> headers = Propagation.honeycombHeaderV1().encode(context).get();

        final MockHttpServletRequestBuilder builder = get("/basic-get");
        headers.forEach((k,v) -> builder.header(k, v));
        mvc.perform(builder);
        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).containsEntry("trace-field", "abc");
    }


    @Test
    public void GIVEN_aClientConfiguredByTheBeeline_WHEN_usingHoneyClientToSendEvent_EXPECT_metaFieldsToBePresent() {
        client.send(of("field1", "value1", "field2", "value2"));

        final Map<String, Object> eventFields = captureEventData();

        assertThat(eventFields).contains(
            entry("field1", "value1"),
            entry("field2", "value2")
        );
        checkMetaFields(eventFields);
    }

    @Test
    public void WHEN_makingARequestToEndpointWithAOPAnnotation_EXPECT_aopSpanToBeSubmitted() throws Exception {
        mvc.perform(get("/annotation-span").header("x-application-header", "fish"));

        final List<ResolvedEvent> eventFields = captureNoOfEvents(2);
        final Map<String, Object> aopSpanFields = eventFields.get(0).getFields();
        final Map<String, Object> requestSpanFields = eventFields.get(1).getFields();

        assertThat(aopSpanFields).containsEntry("name", "AnnotatedController");
        assertThat(aopSpanFields).containsEntry("app-header", "fish");
        assertThat(aopSpanFields).containsEntry("spring.method.result", "bacteria");
        assertThat(aopSpanFields).containsEntry("user-field", "insects");

        assertThat(requestSpanFields).containsEntry("response.header.content_type", "text/plain;charset=UTF-8");
        assertThat(requestSpanFields).containsEntry("response.status_code", 200);
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

    private String tryGetLocalHostname() {
        try {
            final InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostName();
        } catch (final UnknownHostException e) {
            return "UNKNOWN_HOST";
        }
    }

    private Map<String, Object> captureEventData() {
        verify(transport).submit(eventCaptor.capture());
        return eventCaptor.getValue().getFields();
    }

}
