package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.*;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.transport.Transport;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.honeycomb.beeline.tracing.propagation.HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER;
import static io.honeycomb.beeline.tracing.propagation.W3CPropagationCodec.W3C_TRACEPARENT_HEADER;
import static io.honeycomb.beeline.tracing.propagation.ServletTestingUtils.*;
import static io.restassured.RestAssured.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BeelineServletFilterTest {
    private static final String CONTEXT_PATH = "/api";
    private static final String HELLO_PATH = "/hello";
    private static final String FORWARD_PATH = "/forward";
    private static final String INCLUDE_PATH = "/include";
    private static final String ERRORING_PATH = "/erroring";
    private static final String EXCEPTION_PATH = "/exception";
    private static final String ERROR_PATH = "/error";
    private static final String SIMPLE_ASYNC_PATH = "/simple-async";
    private static final String TIMEOUT_ASYNC_PATH = "/timeout-async";
    private static final String CHILD_SPAN_ASYNC_PATH = "/child-span-async";
    private static final String OTHER_ASYNC_FIELD = "an.async.field";
    private static final String OTHER_ASYNC_VALUE= "test";
    private static final String CHILD_ASYNC_SPAN_NAME = "child_async";
    private static final String ERROR_SERVLET_FIELD = "err_field";
    private static final String ERROR_SERVLET_FIELD_VALUE = "test1";

    private ServletTestingUtils.Server server;

    @Mock
    private Transport mockTransport;
    @Captor
    private ArgumentCaptor<ResolvedEvent> resolvedEventCaptor;

    @Before
    public void setup() throws Exception {
        stubMockTransport(mockTransport);
        final HoneyClient honeyClient = createHoneyClient(mockTransport);
        final Beeline beeline = createBeeline(honeyClient);

        ServletContextHandler servletContext = new ServletContextHandler();
        servletContext.setContextPath(CONTEXT_PATH);
        servletContext.addServlet(HelloServlet.class, HELLO_PATH);
        servletContext.addServlet(ExceptionServlet.class, EXCEPTION_PATH);
        servletContext.addServlet(ForwardServlet.class, FORWARD_PATH);
        servletContext.addServlet(IncludeServlet.class, INCLUDE_PATH);
        servletContext.addServlet(TimedOutAsyncServlet.class, TIMEOUT_ASYNC_PATH);
        servletContext.addServlet(ErroringServlet.class, ERRORING_PATH);
        servletContext.addServlet(new ServletHolder(new ErrorServlet(beeline)), ERROR_PATH);
        servletContext.addServlet(SimpleAsyncServlet.class, SIMPLE_ASYNC_PATH);
        servletContext.addServlet(new ServletHolder(new ChildSpanAsyncServlet(beeline)), CHILD_SPAN_ASYNC_PATH);
        final ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.addErrorPage(ErrorPageErrorHandler.GLOBAL_ERROR_PAGE, ERROR_PATH);
        servletContext.setErrorHandler(errorHandler);
        addBeelineFilterToServletContext(servletContext, beeline);
        server = new ServletTestingUtils.Server(servletContext, honeyClient);
        port = server.getPort();
    }

    @After
    public void destroy() throws Exception {
        server.stop();
    }

    public static class HelloServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    public static class ForwardServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            request.getServletContext().getRequestDispatcher(HELLO_PATH).forward(request, response);
        }
    }

    public static class IncludeServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            request.getServletContext().getRequestDispatcher(HELLO_PATH).include(request, response);
        }
    }

    public static class ErrorServlet extends HttpServlet {
        private final Beeline beeline;

        public ErrorServlet(Beeline beeline) {
            this.beeline = beeline;
        }

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            beeline.getActiveSpan().addField(ERROR_SERVLET_FIELD, ERROR_SERVLET_FIELD_VALUE);
        }
    }

    public static class ErroringServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.sendError(503);
        }
    }

    public static class ExceptionServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            throw new TestException();
        }
    }

    private static class TestException extends RuntimeException {}


    public static class SimpleAsyncServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
            AsyncContext asyncContext = req.startAsync();
            asyncContext.start(asyncContext::complete);
        }
    }

    public static class TimedOutAsyncServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
            AsyncContext asyncContext = req.startAsync();
            asyncContext.setTimeout(1);
            asyncContext.start(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    asyncContext.complete();
                }
            });
        }
    }

    public static class ChildSpanAsyncServlet extends HttpServlet {
        private final Beeline beeline;

        public ChildSpanAsyncServlet(Beeline beeline) {
            this.beeline = beeline;
        }

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) {
            AsyncContext asyncContext = req.startAsync();
            final Runnable tracedRunnable = beeline.getTracer().traceRunnable("child_async", () -> {
                beeline.getActiveSpan().addField(OTHER_ASYNC_FIELD, OTHER_ASYNC_VALUE);
                ((HttpServletResponse) asyncContext.getResponse()).setStatus(201);
                asyncContext.complete();
            });
            asyncContext.start(tracedRunnable);
        }
    }

    @Test
    public void testGet() {
        get(fullPath(HELLO_PATH)).then().assertThat().statusCode(200);
        verify(mockTransport).submit(resolvedEventCaptor.capture());
        final ResolvedEvent resolvedEvent = resolvedEventCaptor.getValue();
        assertEquals("http_get", resolvedEvent.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        checkCoreFields(resolvedEvent,"REQUEST", 200, HELLO_PATH);
    }

    /**
     * Exception that is not mapped to error handler. An error dispatch will happen, will be invoked by the container
     * after the REQUEST dispatch has finished. We should mark the REQUEST span as an error, and not set the HTTP status
     * field as we don't know exactly what it resolve to.
     */
    @Test
    public void testException() {
        get(fullPath(EXCEPTION_PATH)).then().assertThat().statusCode(500);
        verify(mockTransport, times(1)).submit(resolvedEventCaptor.capture());
        final ResolvedEvent resolvedEvent = resolvedEventCaptor.getValue();
        assertEquals(SERVICE_NAME, resolvedEvent.getFields().get(TraceFieldConstants.SERVICE_NAME_FIELD));
        assertEquals(DispatcherType.REQUEST.name(), resolvedEvent.getFields().get(BeelineServletFilter.DISPATCHER_TYPE_FIELD));
        assertNull(resolvedEvent.getFields().get(TraceFieldConstants.STATUS_CODE_FIELD));
        assertEquals(fullPath(EXCEPTION_PATH), resolvedEvent.getFields().get(TraceFieldConstants.REQUEST_PATH_FIELD));
        assertEquals("TestException", resolvedEvent.getFields().get(TraceFieldConstants.REQUEST_ERROR_FIELD));
    }

    @Test
    public void testForward() {
        get(fullPath(FORWARD_PATH)).then().assertThat().statusCode(200);
        verify(mockTransport, times(2)).submit(resolvedEventCaptor.capture());
        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        final ResolvedEvent rootSpan = getSingleSpanByName(resolvedEvents, "http_get");
        final ResolvedEvent childSpan = getSingleSpanByName(resolvedEvents, "http_forward");
        checkParentChildRelation(rootSpan, childSpan);
        checkCoreFields(rootSpan,"REQUEST", 200, FORWARD_PATH);
        checkCoreFields(childSpan,"FORWARD", 200, HELLO_PATH);
    }

    @Test
    public void testInclude() {
        get(fullPath(INCLUDE_PATH)).then().assertThat().statusCode(200);
        verify(mockTransport, times(2)).submit(resolvedEventCaptor.capture());
        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        final ResolvedEvent rootSpan = getSingleSpanByName(resolvedEvents, "http_get");
        final ResolvedEvent childSpan = getSingleSpanByName(resolvedEvents, "http_include");
        checkParentChildRelation(rootSpan, childSpan);
        checkCoreFields(rootSpan, "REQUEST", 200, INCLUDE_PATH);
        checkCoreFields(childSpan, "INCLUDE", 200, INCLUDE_PATH);
    }

    /**
     * Error mapped to registered error page. Therefore child-span ERROR dispatch span created and actual HTTP
     * status is recorded in spans.
     */
    @Test
    public void testErroring() {
        get(fullPath(ERRORING_PATH)).then().assertThat().statusCode(503);
        verify(mockTransport, times(2)).submit(resolvedEventCaptor.capture());
        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        final ResolvedEvent rootSpan = getSingleSpanByName(resolvedEvents, "http_get");
        final ResolvedEvent childSpan = getSingleSpanByName(resolvedEvents, "http_error");
        checkParentChildRelation(rootSpan, childSpan);
        checkCoreFields(rootSpan,"REQUEST", 503, ERRORING_PATH);
        checkCoreFields(childSpan,"ERROR", 503, ERROR_PATH);
        assertEquals(ERROR_SERVLET_FIELD_VALUE, childSpan.getFields().get(ERROR_SERVLET_FIELD));
    }

    @Test
    public void testSimpleAsync() {
        get(fullPath(SIMPLE_ASYNC_PATH)).then().assertThat().statusCode(200);
        verify(mockTransport, times(1)).submit(resolvedEventCaptor.capture());
        final ResolvedEvent event = resolvedEventCaptor.getValue();
        assertEquals("http_get", event.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        checkCoreFields(event,"REQUEST", 200, SIMPLE_ASYNC_PATH);
        assertTrue((boolean)event.getFields().get(BeelineServletFilter.ASYNC_DISPATCH_FIELD));
    }

    /**
     * In this scenario, a timeout occurs during an async REQUEST dispatch span. The HTTP response status will be
     * determined by the container when the span is closed, so we leave them unset. We mark the span as an error
     * though.
     */
    @Test
    public void testTimeoutAsync() {
        get(fullPath(TIMEOUT_ASYNC_PATH)).then().assertThat().statusCode(500);
        verify(mockTransport, times(1)).submit(resolvedEventCaptor.capture());
        final ResolvedEvent event = resolvedEventCaptor.getValue();
        assertEquals("http_get", event.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        assertTrue((boolean)event.getFields().get(BeelineServletFilter.ASYNC_DISPATCH_FIELD));
        assertEquals(BeelineServletFilter.ASYNC_TIMEOUT_ERROR, event.getFields().get(TraceFieldConstants.REQUEST_ERROR_FIELD));
        assertNull(event.getFields().get(TraceFieldConstants.STATUS_CODE_FIELD));
        assertEquals(fullPath(TIMEOUT_ASYNC_PATH), event.getFields().get(TraceFieldConstants.REQUEST_PATH_FIELD));
    }

    @Test
    public void testChildSpanAsync() {
        get(fullPath(CHILD_SPAN_ASYNC_PATH)).then().assertThat().statusCode(201);
        verify(mockTransport, timeout(1000).times(2)).submit(resolvedEventCaptor.capture());
        final List<ResolvedEvent> resolvedEvents = resolvedEventCaptor.getAllValues();
        final ResolvedEvent rootSpan = getSingleSpanByName(resolvedEvents, "http_get");
        final ResolvedEvent childSpan = getSingleSpanByName(resolvedEvents, CHILD_ASYNC_SPAN_NAME);
        checkParentChildRelation(rootSpan, childSpan);
        checkCoreFields(rootSpan,"REQUEST", 201, CHILD_SPAN_ASYNC_PATH);
        assertTrue((boolean)rootSpan.getFields().get(BeelineServletFilter.ASYNC_DISPATCH_FIELD));
        assertEquals(CHILD_ASYNC_SPAN_NAME, childSpan.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        assertEquals(OTHER_ASYNC_VALUE, childSpan.getFields().get(OTHER_ASYNC_FIELD));
    }

    @Test
    public void testContinuingExternalTraceFromHoneycombHeader() {
        final String traceId = "current-trace-1";
        final String parentId = "parent-span-1";
        final PropagationContext context = new PropagationContext(traceId, parentId, null, Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> headers = Propagation.honeycombHeaderV1()
                                        .encode(context)
                                        .orElseThrow(() -> new  AssertionError("Propagation context test setup errored"));

        final String headerValue = headers.get(HONEYCOMB_TRACE_HEADER);
        given().header(HONEYCOMB_TRACE_HEADER, headerValue).when().get(fullPath(HELLO_PATH)).then().assertThat().statusCode(200);
        verify(mockTransport).submit(resolvedEventCaptor.capture());
        final ResolvedEvent resolvedEvent = resolvedEventCaptor.getValue();
        assertEquals("http_get", resolvedEvent.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        assertEquals(traceId, resolvedEvent.getFields().get(TraceFieldConstants.TRACE_ID_FIELD));
        assertEquals(parentId, resolvedEvent.getFields().get(TraceFieldConstants.PARENT_ID_FIELD));
        checkCoreFields(resolvedEvent,"REQUEST", 200, HELLO_PATH);
    }

    @Test
    public void testContinuingExternalTraceFromW3CHeader() {
        final String traceId = "0af7651916cd43dd8448eb211c80319c";
        final String parentId = "b7ad6b7169203331";
        final PropagationContext context = new PropagationContext(traceId, parentId, null,
                Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> headers = Propagation.w3c()
                .encode(context)
                .orElseThrow(() -> new AssertionError("Propagation context test setup errored"));

        final String headerValue = headers.get(W3C_TRACEPARENT_HEADER);
        given().header(W3C_TRACEPARENT_HEADER, headerValue).when().get(fullPath(HELLO_PATH)).then().assertThat()
                .statusCode(200);
        verify(mockTransport).submit(resolvedEventCaptor.capture());
        final ResolvedEvent resolvedEvent = resolvedEventCaptor.getValue();
        assertEquals("http_get", resolvedEvent.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        assertEquals(traceId, resolvedEvent.getFields().get(TraceFieldConstants.TRACE_ID_FIELD));
        assertEquals(parentId, resolvedEvent.getFields().get(TraceFieldConstants.PARENT_ID_FIELD));
        checkCoreFields(resolvedEvent, "REQUEST", 200, HELLO_PATH);
    }

    @Test
    public void testContinuingExternalTraceFromHoneycombWhenW3CAlsoPresent() {
        final String w3cTraceId = "0af7651916cd43dd8448eb211c812345";
        final String w3cParentId = "b7ad6b7169212345";
        final PropagationContext w3cContext = new PropagationContext(w3cTraceId, w3cParentId, null,
                Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> w3cHeaders = Propagation.w3c()
                .encode(w3cContext)
                .orElseThrow(() -> new AssertionError("Propagation context test setup errored"));

        final String honeycombTraceId = "0af7651916cd43dd8448eb211c856789";
        final String honeycombParentId = "b7ad6b7169256789";
        final PropagationContext honeycombContext = new PropagationContext(honeycombTraceId, honeycombParentId, null,
                Collections.singletonMap("trace-field", "abc"));
        final Map<String, String> honeycombHeaders = Propagation.honeycombHeaderV1()
                .encode(honeycombContext)
                .orElseThrow(() -> new AssertionError("Propagation context test setup errored"));

        final String w3cHeaderValue = w3cHeaders.get(W3C_TRACEPARENT_HEADER);
        final String honeycombHeaderValue = honeycombHeaders.get(HONEYCOMB_TRACE_HEADER);
        given().header(W3C_TRACEPARENT_HEADER, w3cHeaderValue)
                .header(HONEYCOMB_TRACE_HEADER, honeycombHeaderValue).when().get(fullPath(HELLO_PATH)).then()
                .assertThat()
                .statusCode(200);
        verify(mockTransport).submit(resolvedEventCaptor.capture());
        final ResolvedEvent resolvedEvent = resolvedEventCaptor.getValue();
        assertEquals("http_get", resolvedEvent.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
        assertEquals(honeycombTraceId, resolvedEvent.getFields().get(TraceFieldConstants.TRACE_ID_FIELD));
        assertEquals(honeycombParentId, resolvedEvent.getFields().get(TraceFieldConstants.PARENT_ID_FIELD));
        checkCoreFields(resolvedEvent, "REQUEST", 200, HELLO_PATH);
    }

    @Test
    public void testPost() {
        post(fullPath(HELLO_PATH)).then().assertThat().statusCode(200);
        verify(mockTransport).submit(resolvedEventCaptor.capture());
        final ResolvedEvent resolvedEvent = resolvedEventCaptor.getValue();
        assertEquals("http_post", resolvedEvent.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD));
    }

    private void checkParentChildRelation(final ResolvedEvent parent, final ResolvedEvent child) {
        assertTrue(parent.getTimestamp() <= child.getTimestamp());
        assertNotNull(parent.getFields().get(TraceFieldConstants.SPAN_ID_FIELD));
        assertNotNull(parent.getFields().get(TraceFieldConstants.TRACE_ID_FIELD));
        assertNotNull(child.getFields().get(TraceFieldConstants.SPAN_ID_FIELD));
        assertNotNull(child.getFields().get(TraceFieldConstants.TRACE_ID_FIELD));
        assertEquals(parent.getFields().get(TraceFieldConstants.SPAN_ID_FIELD), child.getFields().get(TraceFieldConstants.PARENT_ID_FIELD));
        assertEquals(parent.getFields().get(TraceFieldConstants.TRACE_ID_FIELD), child.getFields().get(TraceFieldConstants.TRACE_ID_FIELD));
    }

    private String fullPath(final String path) {
        return CONTEXT_PATH + path;
    }

    private ResolvedEvent getSingleSpanByName(final List<ResolvedEvent> resolvedEvents, final String spanName) {
        final List<ResolvedEvent> matchingName = resolvedEvents.stream()
            .filter(re -> re.getFields().get(TraceFieldConstants.SPAN_NAME_FIELD).equals(spanName))
            .collect(Collectors.toList());
        if (matchingName.size() != 1) {
            fail("Found " + matchingName.size() + " spans when expected to find a single span with name: " + spanName);
        }
        return matchingName.get(0);
    }

    private void checkCoreFields(final ResolvedEvent resolvedEvent,
                                 final String dispatcherType,
                                 final Integer statusCode,
                                 final String path) {
        assertEquals(SERVICE_NAME, resolvedEvent.getFields().get(TraceFieldConstants.SERVICE_NAME_FIELD));
        assertEquals(dispatcherType, resolvedEvent.getFields().get(BeelineServletFilter.DISPATCHER_TYPE_FIELD));
        assertEquals(statusCode, resolvedEvent.getFields().get(TraceFieldConstants.STATUS_CODE_FIELD));
        assertEquals(fullPath(path), resolvedEvent.getFields().get(TraceFieldConstants.REQUEST_PATH_FIELD));
    }
}
