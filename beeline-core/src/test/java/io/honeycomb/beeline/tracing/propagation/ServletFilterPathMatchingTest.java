package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.transport.Transport;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;

import static io.honeycomb.beeline.tracing.propagation.ServletTestingUtils.*;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.port;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ServletFilterPathMatchingTest {
    private static final String CONTEXT_PATH = "/api";
    private static final String PATH_1 = "/one";
    private static final String PATH_2 = "/two";
    private static final String PATH_1_2 = "/one/two";

    @Mock
    private Transport mockTransport;
    @Captor
    private ArgumentCaptor<ResolvedEvent> resolvedEventCaptor;

    private ServletTestingUtils.Server server;

    @After
    public void destroy() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void whenNoDenyOrAllowlisting_thenAllSpansCollected() throws Exception {
        initServerWithFilterPathMatching(Collections.emptyList(), Collections.emptyList());
        callAllPaths();

        verify(mockTransport, times(3)).submit(resolvedEventCaptor.capture());

        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        assertSpanExistsWithPath(fullPath(PATH_1), resolvedEvents);
        assertSpanExistsWithPath(fullPath(PATH_2), resolvedEvents);
        assertSpanExistsWithPath(fullPath(PATH_1_2), resolvedEvents);
    }

    @Test
    public void whenAllowlistingOfPathOneGreedily_thenTwoSpansCollected() throws Exception {
        initServerWithFilterPathMatching(Collections.singletonList("/api/one/**"), Collections.emptyList());

        callAllPaths();

        verify(mockTransport, times(2)).submit(resolvedEventCaptor.capture());

        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        assertSpanExistsWithPath(fullPath(PATH_1), resolvedEvents);
        assertSpanExistsWithPath(fullPath(PATH_1_2), resolvedEvents);
    }

    @Test
    public void whenDenylistExactPathOne_thenTwoSpansCollected() throws Exception {
        initServerWithFilterPathMatching(Collections.emptyList(), Collections.singletonList("/api/one"));
        callAllPaths();

        verify(mockTransport, times(2)).submit(resolvedEventCaptor.capture());

        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        assertSpanExistsWithPath(fullPath(PATH_2), resolvedEvents);
        assertSpanExistsWithPath(fullPath(PATH_1_2), resolvedEvents);
    }

    @Test
    public void whenDenylistRootGreedily_thenNoSpansCollected() throws Exception {
        initServerWithFilterPathMatching(Collections.emptyList(), Collections.singletonList("/api/**"));
        callAllPaths();
        verify(mockTransport, never()).submit(any());
    }

    @Test
    public void whenAllowlistPathOneGreedily_andDenylistChildPathTwo_thenOnlyPathOneCollected() throws Exception {
        initServerWithFilterPathMatching(Collections.singletonList("/api/one/**"), Collections.singletonList("/api/one/two"));
        callAllPaths();
        verify(mockTransport, times(1)).submit(resolvedEventCaptor.capture());

        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        assertSpanExistsWithPath(fullPath(PATH_1), resolvedEvents);
    }

    private void initServerWithFilterPathMatching(final List<String> allowlist, final List<String> denylist) throws Exception {
        stubMockTransport(mockTransport);
        final HoneyClient honeyClient = createHoneyClient(mockTransport);
        final Beeline beeline = createBeeline(honeyClient);

        ServletContextHandler servletContext = new ServletContextHandler();
        servletContext.setContextPath(CONTEXT_PATH);
        servletContext.addServlet(TestServlet.class, PATH_1);
        servletContext.addServlet(TestServlet.class, PATH_2);
        servletContext.addServlet(TestServlet.class, PATH_1_2);

        addBeelineFilterToServletContext(servletContext, beeline, allowlist, denylist);

        server = new ServletTestingUtils.Server(servletContext, honeyClient);
        port = server.getPort();
    }

    private String fullPath(final String servletPath) {
        return CONTEXT_PATH + servletPath;
    }

    private void assertSpanExistsWithPath(final String path, final List<ResolvedEvent> resolvedEvents) {
        Assert.assertEquals(1, resolvedEvents.stream().filter(re -> re.getFields().get(TraceFieldConstants.REQUEST_PATH_FIELD).equals(path)).count());
    }

    private void callAllPaths() {
        get(fullPath(PATH_1)).then().assertThat().statusCode(200);
        get(fullPath(PATH_2)).then().assertThat().statusCode(200);
        get(fullPath(PATH_1_2)).then().assertThat().statusCode(200);
    }

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
