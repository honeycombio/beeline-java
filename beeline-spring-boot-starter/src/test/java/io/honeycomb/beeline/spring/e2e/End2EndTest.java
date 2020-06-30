package io.honeycomb.beeline.spring.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.honeycomb.beeline.tracing.ids.W3CTraceIdProvider;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

/**
 * These tests start a web application context with a running instance of tomcat, so we call the server via HTTP in
 * these tests (using RestAssured's fluent API). There is also a WireMock instance to simulate a back end service, which
 * is used to test trace propagation over the wire.
 * <p>
 * The web server endpoints that RestAssured are defined in {@link End2EndTestController}. They are generally simple
 * stubs to verify our implementation against a specific behaviour of the Spring MVC framework.
 * <p>
 * Additional Spring context configuration happens in {@link End2EndTestConfig}.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = End2EndTest.ASYNC_TIMEOUT_TEST_PROPERTY)
@ActiveProfiles("request-test") // means application-request-test.properties is picked up
public class End2EndTest {
    /* This is for the async timeout test case and ensures the timeout exception is thrown after 1s. */
    public static final String ASYNC_TIMEOUT_TEST_PROPERTY = "spring.mvc.async.request-timeout=1s";

    @LocalServerPort
    private int port;
    @SpyBean // Spy on the sampler so we can selectively make it not sample
    private TraceSampler<String> sampler;
    private ArgumentCaptor<ResolvedEvent> eventCaptor = ArgumentCaptor.forClass(ResolvedEvent.class);
    // replace the transport from the BeelineAutoconfig with mock, so we can capture and inspect events to be sent to honeycomb
    @MockBean
    private Transport transport;

    public void setTransportStubs() {
        when(transport.submit(any(ResolvedEvent.class))).thenReturn(true);
        when(transport.getResponseObservable()).thenReturn(mock(ResponseObservable.class));
    }

    @Before
    public void setUp() {
        setTransportStubs();
        RestAssured.port = port;
    }

    // Start mock web service with a simple stub endpoint
    @Rule
    public WireMockRule mock = new WireMockRule(WireMockConfiguration.options().port(8089)
        .notifier(new Slf4jNotifier(true))); // log request info verbosely (see logback-test.xml to enable it)

    private void mockBackendService() {
        mock.stubFor(
            WireMock.get(urlEqualTo("/backend-service"))
                .willReturn(aResponse().withBody(
                    "Backend response text"
                )));
    }

    // @formatter:off  <-- preserve formatting
    @Test
    public void GIVEN_aWebServiceConnectedToAnotherService_WHEN_sendingRequestWithTraceHeader_EXPECT_TraceToPropagate() {
        // SETUP mock
        mockBackendService();

        // WHEN hitting an instrumented web service with a trace header
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/call-backend")

        .then()
            .statusCode(OK.value())
            .body(is("Backend response text"));

        // THEN expect it to propagate the trace downstream
        verify(
            getRequestedFor(urlPathEqualTo("/backend-service"))
            .withHeader(
                "x-honeycomb-trace",
                matching("1;.*trace_id=abc,parent_id=.*")));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent clientEvent = resolvedEvents.get(0);
        final ResolvedEvent serverEvent = resolvedEvents.get(1);

        assertThat(
                fieldStringOf(clientEvent, "trace.parent_id"))
            .isEqualTo(
                fieldStringOf(serverEvent, "trace.span_id"));
        assertThat(clientEvent.getFields())
            .contains(
                entry("type", "http_client"),
                entry("client.request.method", "GET"),
                entry("client.response.status_code", 200),
                entry("client.request.path", "/backend-service"),
                entry("trace.trace_id", "abc"))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "request.error");

        assertThat(serverEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/call-backend"),
                entry("trace.trace_id", "abc"),
                entry("response.status_code", 200),
                entry("trace.parent_id", "123"))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "request.error");
    }

    @Test
    public void GIVEN_traceIsNotSampled_WHEN_sendingRequestWithTraceHeader_EXPECT_NoSpansToBeGenerated() {
        // SETUP mock and dont sample
        mockBackendService();
        dontSample();

        // WHEN hitting an instrumented web service with a trace header
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/call-backend")

        .then()
            .statusCode(OK.value())
            .body(is("Backend response text"));

        // THEN expect it to propagate the trace downstream
        verify(
            getRequestedFor(urlPathEqualTo("/backend-service"))
            .withoutHeader("x-honeycomb-trace"));

        verifyNoInteractions(transport);
    }

    @Test
    public void GIVEN_traceIsNotSampled_WHEN_sendingRequestWithoutTraceHeader_EXPECT_noSpanToBeSubmitted() {
        dontSample();
        given()
            .get("/receive-forward")

        .then()
            .statusCode(200)
            .body(is("Forward Received"));

        verifyNoInteractions(transport);
    }

    @Test
    public void GIVEN_noTraceHeader_WHEN_callingEndpoint_EXPECT_spanToHaveGeneratedIds() {
        given()
            .get("/receive-forward")

        .then()
            .statusCode(200)
            .body(is("Forward Received"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent requestEvent = resolvedEvents.get(0);

        assertThat(fieldStringOf(requestEvent, "trace.span_id")).satisfies(W3CTraceIdProvider::validateSpanId);
        assertThat(fieldStringOf(requestEvent, "trace.trace_id")).satisfies(W3CTraceIdProvider::validateTraceId);
        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/receive-forward"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("response.status_code", 200),
                entry("step2", true))
            .doesNotContainKeys(
                "trace.parent_id",
                "spring.request.async_dispatch",
                "request.error");
    }

    @Test
    public void GIVEN_anEndpointThatIssuesAForwardToAnother_WHEN_callingEndpoint_EXPECT_responseFromSecondEndpoint() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/forward-request")

        .then()
            .statusCode(200)
            .body(is("Forward Received"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent forwardEvent = resolvedEvents.get(0);
        final ResolvedEvent requestEvent = resolvedEvents.get(1);

        assertThat(
                fieldStringOf(forwardEvent, "trace.parent_id"))
            .isEqualTo(
                fieldStringOf(requestEvent, "trace.span_id"));

        assertThat(forwardEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/receive-forward"),
                entry("spring.request.dispatcher_type", "FORWARD"),
                entry("response.status_code", 200),
                entry("trace.trace_id", "abc"),
                entry("step2", true))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "request.error");

        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/forward-request"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("response.status_code", 200),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("step1", true))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "request.error");
    }

    @Test
    public void GIVEN_aAnEndpointThatReturnsAnAsyncValue_WHEN_callingEndpoint_EXPECT_AsyncSpanToBeSubmitted() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/receive-forward-async")

        .then()
            .statusCode(200)
            .body(is("Forward Received Async"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent forwardEvent = resolvedEvents.get(0);

        assertThat(forwardEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/receive-forward-async"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("spring.request.async_dispatch", true),
                entry("response.status_code", 200),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("step2", true))
            .doesNotContainKeys(
                "request.error");
    }

    @Test
    public void GIVEN_aForwardToAnEndpointThatReturnsAnAsyncValue_WHEN_callingEndpoint_EXPECT_AsyncSpansToBeSubmitted() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/forward-to-async")

            .then()
            .statusCode(200)
            .body(is("Forward Received Async"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent forwardEvent = resolvedEvents.get(0);
        final ResolvedEvent requestEvent = resolvedEvents.get(1);

        assertThat(
                fieldStringOf(forwardEvent, "trace.parent_id"))
            .isEqualTo(
                fieldStringOf(requestEvent, "trace.span_id"));
        assertThat(forwardEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/receive-forward-async"),
                entry("spring.request.dispatcher_type", "FORWARD"),
                entry("response.status_code", 200),
                entry("spring.request.async_dispatch", true),
                entry("trace.trace_id", "abc"),
                entry("step2", true))
            .doesNotContainKeys(
                "request.error");

        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/forward-to-async"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("response.status_code", 200),
                entry("spring.request.async_dispatch", true),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("step1", true))
            .doesNotContainKeys(
                "request.error");
    }

    @Test
    public void WHEN_callingPathThatDoesntExit_EXPECT_spanToReport404Status() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/path-doesnt-exit")

            .then()
            .statusCode(NOT_FOUND.value())
            .body("error", Matchers.equalTo("Not Found"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent requestEvent = resolvedEvents.get(0);

        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/path-doesnt-exit"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("response.status_code", 404),
                entry("spring.request.matched_pattern", "/**"),
                entry("spring.request.handler_type", "ResourceHttpRequestHandler"))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "request.error");
    }

    @Test
    public void GIVEN_AnEndpointThatIsProtectedByAServletFilter_WHEN_callingEndpoint_EXPECT_noSpringRelatedFieldsToBeCaptured() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/reject-request")

            .then()
            .statusCode(UNAUTHORIZED.value())
            .body("error", Matchers.equalTo("Unauthorized"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent requestEvent = resolvedEvents.get(0);

        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/reject-request"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("response.status_code", 401))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "request.error",
                "spring.request.matched_pattern",
                "spring.request.handler_type");
    }

    @Test
    public void GIVEN_AnEndpointThatThrowsAnException_WHEN_forwardedToEndpoint_EXPECT_submittedSpansToHaveErrorDetails() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/forward-to-exception")

        .then()
            .statusCode(500)
            .body("error", Matchers.equalTo("Internal Server Error"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent forwardEvent = resolvedEvents.get(0);
        final ResolvedEvent requestEvent = resolvedEvents.get(1);

        assertThat(fieldStringOf(forwardEvent, "request.error_detail"))
            .contains("Oh boi!");
        assertThat(forwardEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/receive-forward-exception"),
                entry("spring.request.dispatcher_type", "FORWARD"),
                entry("trace.trace_id", "abc"),
                entry("request.error", "NestedServletException"),
                entry("step2", true))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "response.status_code");

        assertThat(fieldStringOf(requestEvent, "request.error_detail"))
            .contains("Oh boi!");
        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/forward-to-exception"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("step1", true))
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "response.status_code");
    }

    @Test
    public void GIVEN_samplingIsFalse_WHEN_forwardedToEndpoint_EXPECT_noSpansToBeSubmitted() {
        dontSample();
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/forward-to-exception")

        .then()
            .statusCode(500)
            .body("error", Matchers.equalTo("Internal Server Error"));

        verifyNoInteractions(transport);
    }

    @Test
    public void GIVEN_anEndpointWithExceptionallyCompletedAsyncReturnValue_WHEN_forwardedToEndpoint_EXPECT_submittedSpansToHaveErrorDetailsButNoResponseStatus() {
        // WHEN hitting an instrumented web service with a trace header
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/forward-to-exception-async")

        .then()
           .statusCode(500)
           .body("error", Matchers.equalTo("Internal Server Error"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent forwardEvent = resolvedEvents.get(0);
        final ResolvedEvent requestEvent = resolvedEvents.get(1);

        assertThat(
                fieldStringOf(forwardEvent, "trace.parent_id"))
            .isEqualTo(
                fieldStringOf(requestEvent, "trace.span_id"));
        assertThat(fieldStringOf(forwardEvent, "request.error_detail"))
            .contains("Oh dear!");
        assertThat(forwardEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/receive-forward-exception-async"),
                entry("spring.request.dispatcher_type", "FORWARD"),
                entry("spring.request.async_dispatch", true),
                entry("trace.trace_id", "abc"),
                entry("request.error", "NestedServletException"),
                entry("step2", true))
            .doesNotContainKeys("response.status_code");

        assertThat(fieldStringOf(requestEvent, "request.error_detail"))
            .contains("Oh dear!");
        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/forward-to-exception-async"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("spring.request.async_dispatch", true),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"),
                entry("step1", true))
            .doesNotContainKeys("response.status_code");
    }

    @Test
    public void GIVEN_anEndpointReturningLongAsyncTask_WHEN_hittingAsyncTimeout_EXPECT_submittedSpansToHaveTimeoutErrorDetailsButNoResponseStatus() {
        // WHEN hitting an instrumented web service with a trace header
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/timeout-request")

            .then()
            .statusCode(SERVICE_UNAVAILABLE.value())
            .body("error", Matchers.equalTo("Service Unavailable"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent requestEvent = resolvedEvents.get(0);

        assertThat(fieldStringOf(requestEvent, "request.error_detail"))
            .contains("Async request timed out after 1000 ms");
        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/timeout-request"),
                entry("request.error", "AsyncTimeout"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("spring.request.async_dispatch", true),
                entry("trace.trace_id", "abc"),
                entry("trace.parent_id", "123"))
            .doesNotContainKeys("response.status_code");
    }

    @Test
    public void GIVEN_anEndpointThatIsAnnotatedToCreateAChildSpan_WHEN_receivingResponse_EXPECT_anExtraSpanToHaveBeenSubmitted() throws Exception {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .header("x-application-header", "fish")
            .get("/annotation-span")
            .then()
            .statusCode(OK.value())
            .body(Matchers.equalTo("bacteria"));

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(2);
        final ResolvedEvent aspectEvent = resolvedEvents.get(0);
        final ResolvedEvent requestEvent = resolvedEvents.get(1);

        assertThat(aspectEvent.getFields())
            .contains(
                entry("name", "AnnotatedController"),
                entry("app-header", "fish"),
                entry("spring.method.result", "bacteria"),
                entry("spring.method.name", "End2EndTestController.annotationSpan(..)"),
                entry("user-field", "insects"),
                entry("type", "annotated_method"),
                entry("trace.trace_id", "abc"))
            .containsKeys("trace.parent_id", "trace.span_id")
            .doesNotContainKeys("spring.request.dispatcher_type", "spring.request.async_dispatch", "response.status_code");

        assertThat(requestEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/annotation-span"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("trace.trace_id", "abc"),
                entry("response.status_code", 200),
                entry("trace.parent_id", "123"))
            .doesNotContainKeys("request.error", "request.error_detail");
    }

    @Test
    public void GIVEN_anEndpointThatThrowsAHandledException_WHEN_callingEndpoint_EXPECT_SpanContainingMappedErrorCode() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/handled-exception")
            .then()
            .statusCode(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED.value());

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent exceptionEvent = resolvedEvents.get(0);

        assertThat(exceptionEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/handled-exception"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("response.status_code", HttpStatus.BANDWIDTH_LIMIT_EXCEEDED.value()),
                entry("trace.trace_id", "abc"),
                entry("custom_error_field", "HandledException"),
                entry("trace.parent_id", "123"))
            .doesNotContainKeys(
                "request.error",
                "request.error_detail",
                "spring.request.async_dispatch");
    }

    @Test
    public void GIVEN_anEndpointThatThrowsAnUnhandledException_WHEN_callingEndpoint_EXPECT_SpanContainingErrorDetailButNoResponseStatus() {
        given()
            .header("x-honeycomb-trace", "1;trace_id=abc,parent_id=123")
            .get("/unhandled-exception")
            .then()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

        final List<ResolvedEvent> resolvedEvents = captureNoOfEvents(1);
        final ResolvedEvent exceptionEvent = resolvedEvents.get(0);

        assertThat(exceptionEvent.getFields())
            .contains(
                entry("type", "http_server"),
                entry("request.method", "GET"),
                entry("request.path", "/unhandled-exception"),
                entry("spring.request.dispatcher_type", "REQUEST"),
                entry("trace.trace_id", "abc"),
                entry("request.error","NestedServletException"),
                entry("trace.parent_id", "123"))
            .containsKeys("request.error_detail")
            .doesNotContainKeys(
                "spring.request.async_dispatch",
                "response.status_code");
    }

    // @formatter:on

    private List<ResolvedEvent> captureNoOfEvents(final int times) {
        // timeout is required because in some cases the Beeline filter's async callback gets invoked with a delay
        Mockito.verify(transport, Mockito.timeout(1000).times(times)).submit(eventCaptor.capture());
        return eventCaptor.getAllValues();
    }

    private String fieldStringOf(final ResolvedEvent event, final String fieldKey) {
        return event.getFields().get(fieldKey).toString();
    }

    private void dontSample() {
        when(sampler.sample(anyString())).thenReturn(0);
    }
}
