package io.honeycomb.beeline.tracing.propagation;

import io.honeycomb.beeline.tracing.*;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.Options;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.Transport;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.DispatcherType;
import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

public class ServletTestingUtils {
    private static final int SET_TO_RANDOM_PORT = 0;
    public static final String SERVICE_NAME = "testService";

    public static Beeline createBeeline(final HoneyClient honeyClient) {
        final TraceSampler<Object> traceSampler = Sampling.alwaysSampler();
        final SpanPostProcessor spanPostProcessor = Tracing.createSpanProcessor(honeyClient, traceSampler);
        final SpanBuilderFactory spanBuilderFactory = Tracing.createSpanBuilderFactory(spanPostProcessor, traceSampler);
        final Tracer tracer = Tracing.createTracer(spanBuilderFactory);
        return Tracing.createBeeline(tracer,spanBuilderFactory);
    }

    public static HoneyClient createHoneyClient(final Transport transport) throws Exception {
        final Options options = LibHoney.options()
            .setApiHost(new URI("http://ignored.com/"))
            .setDataset("blah")
            .setWriteKey("mah")
            .build();
        return new HoneyClient(options, transport);
    }

    public static void stubMockTransport(final Transport mockTransport) {
        lenient().when(mockTransport.submit(any(ResolvedEvent.class))).thenReturn(true);
        final ResponseObservable responseObservable = new ResponseObservable();
        lenient().when(mockTransport.getResponseObservable()).thenReturn(responseObservable);
    }

    public static void addBeelineFilterToServletContext(final ServletContextHandler servletContextHandler, final Beeline beeline) {
        addBeelineFilterToServletContext(servletContextHandler, beeline, Collections.emptyList(), Collections.emptyList());
    }

    public static void addBeelineFilterToServletContext(final ServletContextHandler servletContextHandler, final Beeline beeline, final List<String> filterWhitelist, final List<String> filterBlacklist) {
        final BeelineServletFilter beelineServletFilter = new BeelineServletFilter.Builder()
                                                                .setServiceName(SERVICE_NAME)
                                                                .setIncludePaths(filterWhitelist)
                                                                .setExcludePaths(filterBlacklist)
                                                                .setBeeline(beeline)
                                                                .build();
        servletContextHandler.addFilter(new FilterHolder(beelineServletFilter), "/*", EnumSet.of(DispatcherType.REQUEST,
            DispatcherType.FORWARD, DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.INCLUDE));
    }

    public static class Server {
        private final org.eclipse.jetty.server.Server delegate;
        private final int port;
        private final HoneyClient honeyClient;

        public Server(final ServletContextHandler servletContext, final HoneyClient honeyClient) throws Exception {
            this.honeyClient = honeyClient;
            this.delegate = new org.eclipse.jetty.server.Server(SET_TO_RANDOM_PORT);
            this.delegate.setHandler(servletContext);
            this.delegate.start();
            this.port = ((ServerConnector)delegate.getConnectors()[0]).getLocalPort();
        }

        public int getPort() {
            return port;
        }

        public void stop() throws Exception {
            delegate.stop();
            delegate.join();
            honeyClient.close();
        }
    }
}
