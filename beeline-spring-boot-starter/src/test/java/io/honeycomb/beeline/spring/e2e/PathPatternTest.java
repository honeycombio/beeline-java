package io.honeycomb.beeline.spring.e2e;

import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import io.restassured.RestAssured;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings({"unchecked", "SpringJavaInjectionPointsAutowiringInspection"})
@RunWith(SpringRunner.class)
@ActiveProfiles("path-pattern-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PathPatternTest {
    private ArgumentCaptor<ResolvedEvent> eventCaptor = ArgumentCaptor.forClass(ResolvedEvent.class);

    // replace the transport from the BeelineAutoconfig with mock, so we can capture and inspect events to be sent to honeycomb
    @MockBean
    private Transport transport;

    @LocalServerPort
    private int port;

    private void setTransportStubs() {
        when(transport.submit(any(ResolvedEvent.class))).thenReturn(true);
        when(transport.getResponseObservable()).thenReturn(mock(ResponseObservable.class));
    }

    @Before
    public void setup() {
        setTransportStubs();
        RestAssured.port = port;
    }

    @Test
    public void GIVEN_pathIsOnAllowlist_EXPECT_requestToProduceSpans() {
        // WHEN hitting an instrumented web service with a trace header
        get("/allowlist/basic-get").then().statusCode(OK.value()).body(is("hello"));

        final Map<String, Object> eventFields = captureNoOfEvents(1).get(0).getFields();

        assertThat(eventFields).containsEntry("response.status_code", 200);
        assertThat(eventFields).containsEntry("endpoint", "allowlist");
    }

    @Test
    public void GIVEN_pathIsOnDenylist_EXPECT_requestToNotProduceAnySpans() {
        get("/denylist/basic-get").then().statusCode(OK.value()).body(is("olleh"));

        verify(transport, after(200).never()).submit(any(ResolvedEvent.class));
    }

    @Test
    public void GIVEN_allowlistedRndpointForwardsToDenylistedEndpoints_EXPECT_denylistToNotApplyToForwarding() {
        get("/allowlist/forward-to-denylist").then().statusCode(OK.value()).body(is("olleh"));

        final List<ResolvedEvent> eventFields = captureNoOfEvents(2);
        final ResolvedEvent forwardEvent = eventFields.get(0);
        final ResolvedEvent requestEvent = eventFields.get(1);

        assertThat(forwardEvent.getFields()).containsEntry("endpoint", "denylist");
        assertThat(requestEvent.getFields()).containsEntry("endpoint", "forward");
    }

    private List<ResolvedEvent> captureNoOfEvents(final int times) {
        verify(transport, timeout(1000).times(times)).submit(eventCaptor.capture());
        return eventCaptor.getAllValues();
    }

}
