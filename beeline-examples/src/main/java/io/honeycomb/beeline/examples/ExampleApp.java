package io.honeycomb.beeline.examples;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.propagation.HttpHeaderV1PropagationCodec;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.net.SocketTimeoutException;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ExceptionLogger;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;

class ExampleApp {

    public static void main(String... args) throws Exception {
        HttpRequestHandler requestHandler = new HttpRequestHandler() {
            public void handle(HttpRequest request, HttpResponse response,
                               HttpContext context) throws HttpException, IOException {
                try {
                    startTrace(request);
                    acceptRequest(request);
                    response.setEntity(
                        new StringEntity("Getting customer data.\n",
                            ContentType.TEXT_PLAIN));
                    response.setStatusCode(HttpStatus.SC_OK);
                } finally {
                    hnyConfig.getBeeline().getTracer().endTrace();
                }
            }
        };

        HttpProcessor httpProcessor = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer("JavaBeelineExampleServer"))
            .add(new ResponseContent())
            .add(new ResponseConnControl())
            .build();
        SocketConfig socketConfig = SocketConfig.custom()
            .setSoTimeout(15000)
            .setTcpNoDelay(true)
            .build();
        final HttpServer server = ServerBootstrap.bootstrap()
            .setListenerPort(8080)
            .setHttpProcessor(httpProcessor)
            .setServerInfo("JavaBeelineExampleServer")
            .setSocketConfig(socketConfig)
            .setExceptionLogger(new ExceptionLogger() {
                @Override
                public void log(final Exception ex) {
                    if (ex instanceof SocketTimeoutException || ex instanceof ConnectionClosedException) {
                        return;
                    }
                    ex.printStackTrace();
                }
            })
            .registerHandler("*", requestHandler)
            .create();
        server.start();
        server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown(5, TimeUnit.SECONDS);
                hnyConfig.shutdown(); // close to flush events and release its thread pool
            }
        });
    }

    private static BeelineConfig hnyConfig = new BeelineConfig();
    private static DatabaseService db = new DatabaseService(hnyConfig);

    private static void startTrace(HttpRequest request) {
        Header th = request.getFirstHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER);
        PropagationContext context;
        if (th != null) {
            context = Propagation.honeycombHeaderV1().decode(th.getValue());
        } else {
            context = PropagationContext.emptyContext();
        }

        Span rootSpan = hnyConfig.getBeeline().getSpanBuilderFactory().createBuilder()
            .setSpanName("get-customer-data")
            .setServiceName("customer-db-traced")
            .setParentContext(context)
            .build();
        hnyConfig.getBeeline().getTracer().startTrace(rootSpan);
    }

    public static void acceptRequest(HttpRequest request) {
        Span span = hnyConfig.getBeeline().getActiveSpan();
        try {
            Header cid = request.getFirstHeader("customer-id");
            if (cid != null) {
                db.queryDb(cid.getValue());
                span.addField("result", "OK");
            } else {
                span.addField("result", "No customer id");
            }
        } catch (Exception e) {
            span.addField("result", "Error in processing request")
                .addField("exception-message", e.getMessage());
        }
    }
}
