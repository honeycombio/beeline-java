package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.propagation.HttpServerRequestAdapter;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.sampling.DeterministicTraceSampler;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.utils.Assert;

import java.util.ArrayList;

/**
 * The Beeline class is the main/most-convenient point of interaction with traces in a Beeline-instrumented application.
 * It allows user code to {@linkplain Span#addField(String, Object) add contextual information} to the currently active
 * Span and {@linkplain #startChildSpan(String) to create new nested/child Spans}. This is done by interacting with
 * internal thread-local context. In essence, creating a child pushes onto a stack and {@linkplain Span#close()
 * closing the span} pops it back off. <em>Take care to always close the Spans you create</em> to ensure correct
 * measurements of duration and timely submission to Honeycomb.
 *
 * <h1>Instrumentation</h1>
 * When using this class, it is assumed that your framework instrumentation would have initialized an initial "root"
 * Span, alleviating the burden of having to manage low-level details.
 * For example, the <b>Spring Beeline</b> creates a "root" span for all incoming HTTP requests and closes it when the
 * request returns from user code.
 * <p>
 * However, access to the low level API is given via the delegates returned by {@link #getTracer()} and
 * {@link #getSpanBuilderFactory()}. The Beeline class is essentially a façade to those delegates,
 * narrowing and bundling lower-level APIs to target the most common use-cases.
 *
 * <h1>Sampling</h1>
 * Sampling of traces and Spans can be done in two ways.
 * <p>
 * Firstly, the {@link SpanBuilderFactory} is configured with a "global" sampler
 * (usually {@link DeterministicTraceSampler} that applies its sampling logic to a Span's {@code traceId},
 * and therefore will sample uniformly with a specific sample rate.
 * In addition, if a trace is not sampled, "noop" spans that carry no data are generated tp minimise runtime overhead.
 * <p>
 * Secondly, the factory can be configure with a {@link SpanPostProcessor} that applies sampling just before a Span is
 * submitted to Honeycomb. This allows sampling to be performed based on the contents of the Span.
 * <b>It does so only on Spans that have been sampled by the "global" sampler, because "noop" Spans carry no data</b>.
 * <p>
 * For example, you might decide to sample Spans containing fields indicating error conditions at a high rate,
 * and sample the "happy path" at a lower rate.
 * <p>
 * When the two sampling mechanisms are used together then the effective sample rate is a product of both.
 * However, note that you do not have to use either of the sampling mechanisms and can always configure either one or
 * both to "always sample" with a {@code sampleRate} of 1 (e.g. {@link Sampling#alwaysSampler()} does this).
 *
 * <h1>Thread-safety</h1>
 * See {@link Tracer Tracer's notes on thread-safety}, as the same rules apply to this class.
 *
 * <h1>Propagation to other threads</h1>
 * Because of the use of thread-local context you might find traces breaking as you execute code asynchronously.
 * <p>
 * See {@link Tracer the Tracer's notes on propagation}, which details how to use its API to propagate your traces to
 * other threads.
 *
 * <h1>Propagation to other processes</h1>
 * For example, the <b>Spring Beeline</b> automatically accepts traces by decoding the Honeycomb trace header.
 * It also continues traces downstream when using Spring's RestTemplate.
 * <p>
 * If your framework instrumentation does not automatically propagate traces then you can use
 * {@link Propagation#honeycombHeaderV1()} to encode/decode Honeycombs standard HTTP header.
 * Once decoded, you can use the {@link SpanBuilderFactory} to create new instance and {@link Tracer#startTrace(Span)}
 * to initialize the "root" Span.
 * <p>
 * Encoding takes a {@link PropagationContext}, which you can retrieve with {@link Span#getTraceContext()}.
 */
public class Beeline {
    private final Tracer tracer;
    private final SpanBuilderFactory factory;

    public Beeline(final Tracer tracer, final SpanBuilderFactory factory) {
        Assert.notNull(tracer, "Validation failed: tracer must not be null or empty");
        Assert.notNull(factory, "Validation failed: factory must not be null or empty");

        this.tracer = tracer;
        this.factory = factory;
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
        return tracer.getActiveSpan();
    }

    /**
     * Starts and returns a new span as the child of the previous span.
     * <p>
     * This means the child becomes the new active span (see {@link #getActiveSpan()} on this thread.
     * The old active span (i.e the "parent span") is linked to the child via the Parent ID and via reference.
     * When {@linkplain Span#close()}  closing the child} the parent will be reset to being the active span.
     * <p>
     * Because of the parent and child being linked by reference, the child's lifetime is tied and limited to that
     * of the parent.
     *
     * <h1>Example</h1>
     * <pre>
     * // try-with-resources statement automatically closes the span
     * try (Span childSpan = beeline.startChildSpan("http-call")) {
     *      childSpan.addField("http-url", url);
     *      return httpClient.get(url);
     * }
     * </pre>
     *
     * @param childSpanName to use as the name of the new span - must not be empty.
     * @return new child span.
     * @throws IllegalArgumentException if the argument is null or empty.
     */
    public Span startChildSpan(final String childSpanName) {
        return tracer.startChildSpan(childSpanName);
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
		if (key.startsWith(TraceFieldConstants.USER_FIELD_NAMESPACE)) {
			tracer.getActiveSpan().addField(key, value);
		} else {
		    tracer.getActiveSpan().addField(TraceFieldConstants.USER_FIELD_NAMESPACE + key, value);
		}
    }

    /**
     * A convenience method that starts a trace.
     * <p>
     * Typically this would be invoked when a service receives an external request, but before the request is
     * processed by the service. For example in an HTTP Servlet filter before
     * {@code javax.servlet.FilterChain#doFilter(ServletRequest, ServletResponse)}} is invoked.
     * <p>
     * The {@code parentContext} contains information that is needed to continue traces across process boundaries, e.g.
     * whether the external service making the request was also traced. If the external service was also traced, the
     * span returned by this method will:
     * <p>
     * - share the same trace ID <br>
     * - be the child of the span that represents the call to this service
     * <p>
     * The returned span is also accessible via {@link #getActiveSpan()} because it becomes the current span.
     *
     * @param spanName the name of the span to create
     * @param parentContext the parent context containing inter-process tracing information
     * @param serviceName the name of the service using this method
     * @return the new current span
     * @see Tracer#startTrace(Span)
     * @see io.honeycomb.beeline.tracing.propagation.HttpServerPropagator#startPropagation(HttpServerRequestAdapter) for
     * an existing solution for starting (and closing) traces for incoming requests to an HTTP server.
     */
    public Span startTrace(final String spanName, final PropagationContext parentContext, final String serviceName) {
        return tracer.startTrace(
            factory
                .createBuilder()
                .setSpanName(spanName)
                .setServiceName(serviceName)
                .setParentContext(parentContext)
                .build()
        );
    }

    public Tracer getTracer() {
        return tracer;
    }

    public SpanBuilderFactory getSpanBuilderFactory() {
        return factory;
    }

    /**
     * <p>Close the Beeline by closing SpanBuilderFactory which will internally close the HoneyClient instance.</p>
     * <p>This method should be called in lieu of {@code HoneyClient.close()}. Only one close method needs to be called.
     * {@code beeline.close()} OR {@code honeyClient.close()}</p>
     * <p>This will send any pending events.</p>
     * @see io.honeycomb.libhoney.HoneyClient#close()
     */
    public void close(){
        factory.close();
    }
}

