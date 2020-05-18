package io.honeycomb.beeline;

import java.net.URI;

import io.honeycomb.beeline.builder.BeelineBuilder;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.TransportOptions;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.Options.Builder;

/**
* DefaultBeeline is a singleton representing an initialized Beeline and its collaborator classes
* It uses a simple set of defaults and provides a few convenience wrappers. If you need a more
* specialized configuration, see the Beeline class and our docs at
* https://docs.honeycomb.io/getting-data-in/java/beeline/
*/
public class DefaultBeeline {
    private static DefaultBeeline INSTANCE;

    private SpanBuilderFactory factory;
    private Tracer tracer;
    private Beeline beeline;
    private String serviceName;

    private DefaultBeeline(String dataset, String serviceName, String writeKey,  URI apiHost, TransportOptions transportOptions) {
        Builder builder = LibHoney.options().setDataset(dataset).setWriteKey(writeKey);
        if (apiHost != null) {
            builder.setApiHost(apiHost);
        }

        final HoneyClient client;
        if (transportOptions != null) {
            client = LibHoney.create(builder.build(), transportOptions);
        } else {
           client = LibHoney.create(builder.build());
        }
        final SpanPostProcessor processor = Tracing.createSpanProcessor(client, Sampling.alwaysSampler());
        this.factory = Tracing.createSpanBuilderFactory(processor, Sampling.alwaysSampler());
        this.tracer = Tracing.createTracer(this.factory);
        this.beeline = Tracing.createBeeline(this.tracer, this.factory);
        this.serviceName = serviceName;
    }

    public DefaultBeeline(final BeelineBuilder builder, final String serviceName) {
        this.beeline = builder.build();
        this.factory = beeline.getSpanBuilderFactory();
        this.tracer = beeline.getTracer();
        this.serviceName = serviceName;
    }

    public synchronized static DefaultBeeline getInstance(final String dataset, final String serviceName, final String writeKey) {
        if (INSTANCE == null) {
            INSTANCE = new DefaultBeeline(dataset, serviceName, writeKey, null, null);
        }

        return INSTANCE;
    }

    public synchronized static DefaultBeeline getInstance(final String dataset, final String serviceName, final String writeKey, final URI apiHost, final TransportOptions transportOptions) {
        if (INSTANCE == null) {
            INSTANCE = new DefaultBeeline(dataset, serviceName, writeKey, apiHost, transportOptions);
        }

        return INSTANCE;
    }

    public synchronized static DefaultBeeline getInstance(final BeelineBuilder builder, final String serviceName){
        if (INSTANCE == null) {
            INSTANCE = new DefaultBeeline(builder, serviceName);
        }

        return INSTANCE;
    }

    /**
     * Starts a new span with the given span name. If no trace is active, starts a new trace, and returns
     * the root span. To send this span, call {@link Span#close}.
     *
     * @param spanName will be set in the name field of this span
     * @return a Span
     */
    public Span startSpan(final String spanName) {
        if (this.beeline.getActiveSpan().isNoop()) {
            // start a new trace if no active trace
            final Span span = factory.createBuilder()
            .setSpanName(spanName)
            .setServiceName(serviceName)
            .build();
            tracer.startTrace(span);

            return span;
        }
        return this.beeline.startChildSpan(spanName);
    }

    /**
     * Add a field to the active Span.
     * <p>
     * The field key will be prefixed with "app." to make it part of the "app" namespace for user-added fields.
     * This is to avoid clashes and distinguish it from standard fields provided by automatic instrumentations like
     * the "Spring Beeline".
     *
     * <h1>Example</h1>
     * <pre>
     * if (underAttack) {
     *      beeline.addField("alert-message", "We are under attack!");
     * }
     * </pre>
     *
     * @param key   of the field.
     * @param value of the field.
     * @see #getActiveSpan()
     */
    public void addField(final String key, final Object value) {
        this.beeline.addField(key, value);
    }

    /**
     * Returns the span that is currently active on this thread - the one that is top of the stack.
     * <p>
     * Note, this method is null-safe - a "noop" span is returned if no trace is currently active. This may be due to
     * the current execution not being instrumented or because the trace was not sampled.
     *
     * <h1>Example</h1>
     * <pre>
     * if (underAttack) {
     *      final Span currentSpan = beeline.getActiveSpan()
     *           .addField("alert-message", "We are under attack!")
     *           .addField("alert-level", "red");
     * }
     * </pre>
     *
     * @return the active span.
     */
    public Span getActiveSpan() {
        return this.beeline.getActiveSpan();
    }

    /**
     * Sends the currently active span.
     */
    public void sendActiveSpan() {
        this.beeline.getActiveSpan().close();
    }

    /**
     * getBeeline returns the Beeline in this instance for times when the convenience wrappers aren't sufficient
     *
     * @return Beeline
     */
    public Beeline getBeeline() {
        return this.beeline;
    }

    /**
     * endTrace ends the currently active trace on the thread it is called from
     */
    public void endTrace() {
        this.tracer.endTrace();
    }

    /**
     * Close the Beeline to ensure that any pending events are sent
     */
    public void close() {
        this.beeline.close();
    }
}
