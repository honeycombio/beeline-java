package io.honeycomb.beeline.tracing;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.honeycomb.beeline.tracing.context.ThreadLocalTracingContext;
import io.honeycomb.beeline.tracing.context.TracingContext;
import io.honeycomb.beeline.tracing.utils.ThreadIdentifierObject;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Tracer manages Spans using thread-local context. It has additional helper methods to support advanced use cases
 * and deal with propagation to other threads.
 * <p>
 * This class is meant to be used in conjunction with {@link SpanBuilderFactory}, which is capable of constructing the Span
 * arguments that must be passed into methods like {@link #startTrace(Span)}.
 *
 * <h1>Span vs. TracerSpan</h1>
 * Some methods in this class return {@link TracerSpan}. This is done to clarify that such instances are now managed by
 * the Tracer and user code should no longer interact with the argument. in fact, TracerSpans generally act as
 * decorators around the Span arguments.
 * In any case, most users of the API will want to simply up-cast to Span for cleaner code.
 * <p>
 * On the other hand, where a method returns just Span, expect the instance to longer be managed by the tracer.
 *
 * <h1 id="thread-safety">Thread-safety</h1>
 * This class is thread-safe so that it can be shared across the application. <strong>In fact, it is recommended to use
 * it as a singleton (e.g. as a singleton bean in Spring application), since thread-local state is tied to the instance
 * (it's an instance variable) and not the class (it is not static).</strong>. This state is the hierarchy of Spans
 * representing the trace managed using a thread-local stack.
 * <p>
 * Because of the thread-local nature of the Tracer, TracerSpans are meant to be used on the thread they are created on.
 * They directly interact with the Tracer's thread-local context.
 * <p>
 * There are guards and logging in place to deal with situations where this may be violated.
 *
 * <h2>Propagation to other threads</h2>
 * Because of the use of thread-local context you might find traces breaking as you execute code asynchronously.
 * <p>
 * You can use the {@code trace[Callable/Runnable/...]} methods in this class to wrap various <em>functional
 * interfaces</em> and automatically manage propagation when these get passed to, for instance, a
 * {@code CompletableFuture} or some {@code Executor}.
 * <p>
 * You can also use {@link #startDetachedChildSpan(String)} to create a child span that you can pass on to another
 * thread (assuming you safely establish a <em>happens-before</em> relation), where you can choose to handle them
 * manually or {@link #startTrace(Span) start a new trace}.
 * <p>
 * See the javadoc of each of the mentioned methods for more details.
 */
@SuppressWarnings("ThreadLocalNotStaticFinal") // thread locals are tied to an instance of Tracer
@SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE") // JDK 11 issue https://github.com/spotbugs/spotbugs/issues/756
public class Tracer {
    /*
    The design of this class is centered around the thread-local stack.
    An empty stack means no trace is active.
    Otherwise it is populated with startTrace, grows with startChildSpan, and shrinks when TracerSpans are closed.

    This means that TracerSpan contains a reference to the Tracer in order to be able to close and pop itself off the
    stack.

    pushSpan/popSpan are meant for instances where specific Spans need to be popped out or pushed in - probably
    an uncommon use-case.
    */
    private static final Logger LOG = LoggerFactory.getLogger(Tracer.class);

    private final TracingContext tracingContext;

    private final SpanBuilderFactory factory;

    /**
     * Creates a tracer.
     * You can also use {@link Tracing}'s factory methods.
     *
     * @param factory used to construct spans internally.
     */
    public Tracer(final SpanBuilderFactory factory) {
        Assert.notNull(factory, "Validation failed: factory must not be null");

        this.tracingContext = new ThreadLocalTracingContext();
        this.factory = factory;
    }

    public Tracer( final SpanBuilderFactory factory, final TracingContext context )
    {
        Assert.notNull(factory, "Validation failed: factory must not be null");
        Assert.notNull(context, "Validation failed: context must not be null");

        this.tracingContext = context;
        this.factory = factory;
    }

    /**
     * Starts a trace by attaching the provided Span to the Tracer's thread-local context as the new "root".
     * It is subsequently wrapped and returned as a managed {@link TracerSpan}.
     * See the Tracer's class-level Javadoc for details.
     * <p>
     * The trace should eventually be ended by calling {@link #endTrace()} or {@link Span#close} on the "root" Span.
     * <p>
     * <b>Note</b> that if any Spans are still attached to this Tracer, when this method is called then they will be
     * cleared and submitted to Honeycomb, whilst also logging a warning that trace has been improperly ended.
     *
     * @param span to be attached to this tracer - must not be null.
     * @return a TracerSpan that wraps the attached Span.
     * @throws IllegalArgumentException if argument is null.
     * @see SpanBuilderFactory
     */
    public TracerSpan startTrace(final Span span) {
        Assert.notNull(span, "Validation failed: span must not be null");
        logSpan("Starting new trace", span);

        checkContextIsClean();
        final TracerSpan tracerSpan = createNewTracerSpan(span);
        pushActiveSpan(tracerSpan);
        return tracerSpan;
    }

    /**
     * Attaches the Span to the tracer's thread-local context (i.e. it pushes onto the stack), so that it becomes the
     * new {@linkplain #getActiveSpan() active Span}.
     * <p>
     * It is subsequently wrapped and returned as a managed {@link TracerSpan}.
     * See the Tracer's class-level Javadoc for details.
     * <p>
     * This differs to {@link #startTrace(Span)} in that it does not clear existing context, but rather treats the
     * added Span as the child of the previous active Span (i.e. you are pushing it onto a stack).
     * <p>
     * Note that no checks are performed for whether the new Span's {@code parentSpanId} or {@code traceId}
     * match with the previous Span. This technically allows the nesting of traces without having to clear the previous
     * trace.
     *
     * @param span to attach to the Tracer.
     * @return a TracerSpan that wraps the attached Span.
     * @throws IllegalArgumentException if the argument is null.
     */
    public TracerSpan pushSpan(final Span span) {
        Assert.notNull(span, "Validation failed: span must not be null");
        logSpan("Pushing span", span);

        final TracerSpan tracerSpan = createNewTracerSpan(span);
        pushActiveSpan(tracerSpan);
        return tracerSpan;
    }

    /**
     * Detaches the span identified by the argument's spanId from the thread-local context (i.e. it pops it off the
     * stack) and returns an instance of it that is no longer managed by this Tracer.
     * Any active children of the Span will also be removed from the stack and submitted to Honeycomb.
     * <p>
     * Since the returned Span is no longer managed by this Tracer you must explicitly call {@link Span#close()} on it
     * in order to submit it to Honeycomb.
     * <p>
     * If the Span had a parent, then the parent span will become the new {@linkplain #getActiveSpan() active span}
     * (i.e. you are popping the active span off of a stack).
     *
     * @param spanToPop used to find the correct span.
     * @return a detached copy of the span.
     * @throws IllegalArgumentException if the argument is null.
     */
    public Span popSpan(final Span spanToPop) {
        Assert.notNull(spanToPop, "Validation failed: span must not be null");
        logSpan("Popping span", spanToPop);

        if (spanToPop.isNoop()) {
            return spanToPop;
        }

        if (containsSpan(spanToPop)) {
            boolean hasLogged = false;
            for (final TracerSpan span : tracingContext.get()) {
                if (hasSameSpanId(spanToPop, span)) {
                    popActiveSpan();
                    return span.getDelegate();
                }
                if (!hasLogged) {
                    logSpan("This span's children have not been properly closed - submitting now. " +
                            "This may unintentionally skew the measured durations of child spans.", span);
                    hasLogged = true;
                }
                span.addField(TraceFieldConstants.META_SENT_BY_PARENT_FIELD, true);
                span.close();
            }
        }
        return Span.getNoopInstance();
    }

    /**
     * This ends the trace currently active on this thread and submits the "root" Span to Honeycomb.
     * This is equivalent to calling {@linkplain Span#close()} on the "root" Span instance
     * (e.g. returned by {@link #startTrace(Span)}).
     * <p>
     * If children of the "root" Span are still active, because {@linkplain Span#close()} has not been properly called
     * on them, then the tracer will perform cleanup of the active children regardless. Note that in that case span
     * durations may be skewed.
     */
    public void endTrace() {
        if (tracingContext.size() > 1) {
            /*
            If all child spans have been closed properly, we should end up back at the "root" span, so stack size should
            be 1. This is because TracerSpan#close always sets the parentSpan as the new active span.
            The child spans will be closed as a result of calling close on the root.
            */
            LOG.warn("Called #endTrace while the root span had active child spans.");
        }
        if (tracingContext.size() >= 1) {
            LOG.debug("Ending trace");
            // this implicitly closes child spans (if necessary) and clears the context
            tracingContext.peekLast().close();
        } else {
            LOG.debug("Ending trace, but no trace is active");
        }
    }

    /**
     * Returns the span that is currently active on this thread - the one that is top of the stack.
     * Note, this method is null-safe - a "noop" span is returned if no trace is currently active. This may be due to
     * startTrace not having been called, or because the trace was not sampled (see {@link SpanBuilderFactory}.
     *
     * <h1>Example</h1>
     * <pre>
     * if (underAttack) {
     *      final Span currentSpan = tracer.getActiveSpan()
     *           .addField("alert-message", "We are under attack!")
     *           .addField("alert-level", "red");
     * }
     * </pre>
     *
     * @return the active span.
     */
    public TracerSpan getActiveSpan() {
        final TracerSpan tracerSpan = tracingContext.peekFirst();
        if (tracerSpan == null) { // stack is empty
            return TracerSpan.getNoopTracerSpan();
        }
        return tracerSpan;
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
     * try (Span childSpan = tracer.startChildSpan("http-call")) {
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
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");

        final Span parentSpan = getActiveSpan();
        return startTracedChildSpan(childSpanName, parentSpan);
    }

    /**
     * Starts and returns a new span as the child of the previous span, but without being attached to this Tracer's
     * thread-local context. Thus, {@linkplain #getActiveSpan() the active span} remains unchanged.
     * This also means the returned span's lifetime is not tied to the lifetime of the parent.
     * It is linked to its parent via the {@code parentSpanId} only.
     * <p>
     * This is for advanced use cases when, for instance, you need to track asynchronous computations
     * and call {@link Span#markStart()} and {@link Span#close()} at specific points.
     * <p>
     * The {@code trace*()} methods might be useful if you need to trace lambdas/functional interfaces.
     * <p>
     * If your computation does not cross thread boundaries {@link #startChildSpan(String)} may be more convenient.
     *
     * <h1>Example</h1>
     * <pre>
     * final Span httpServiceSpan = tracer.startDetachedChildSpan("http-call");
     * httpServiceSpan.addField("http-url", url);
     *
     * CompletableFuture
     *      .supplyAsync((){@code ->} {         // on a different thread, so using the tracer won't work here
     *          httpServiceSpan.markStart();    // start measuring time when thread actually starts processing
     *          return httpClient.get(url);
     *      })
     *      .thenApplyAsync(this::convertResponse)
     *      .thenApplyAsync(this::handleResponse)
     *      // add exception message if something went wrong
     *      .exceptionally(e{@code ->} httpServiceSpan.addField("error-message", e.getMessage()))
     *      .thenRun(httpServiceSpan::close);     // close span and submit to Honeycomb
     * </pre>
     *
     * @param childSpanName to use as the name of the new span - must not be empty.
     * @return new detached child span.
     * @throws IllegalArgumentException if the argument is null or empty.
     */
    public Span startDetachedChildSpan(final String childSpanName) {
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");

        final Span parentSpan = getActiveSpan();
        final Span detachedChild = factory.createBuilderFromParent(parentSpan).setSpanName(childSpanName).build();
        logSpan("Starting detached child span", detachedChild);
        return detachedChild;
    }

    ///////////////////////////////////////////////////////////////////////////
    //region lambda wrapper methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Wraps the supplied Runnable so that the currently active trace can be continued within it even if executed
     * asynchronously in a different thread.
     * The child span's lifetime is therefore not tied to the lifetime of the parent.
     * <p>
     * If the returned Runnable is executed on another thread it initializes the tracer's thread-local context for that
     * thread, while leaving the current thread's context unaffected.
     * <p>
     * If run on the same thread while the current trace is still active this will simply start a new child span within
     * the current thread's context.
     *
     * <h1>Example</h1>
     * <pre>
     * CompletableFuture.runAsync(tracer.traceRunnable("http-call", (){@code ->} httpClient.get(url)));
     * </pre>
     *
     * @param childSpanName name of the child Span created for the runnable - must not be null or empty.
     * @param runnable      to wrap - must not be null.
     * @return a traced Runnable.
     * @throws IllegalArgumentException if arguments are null or empty.
     */
    public Runnable traceRunnable(final String childSpanName, final Runnable runnable) {
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");
        Assert.notNull(runnable, "Validation failed: runnable must not be null");

        final TracerSpan parentSpan = getActiveSpan();
        final ThreadIdentifierObject threadId = ThreadIdentifierObject.getCurrentThreadId();
        //noinspection OverlyLongLambda
        return () -> {
            /*
            If active span is noop we are not on an active trace.
            However, it could be that we are on a thread here that has not properly cleaned up its previous trace
            and so interactions with the Tracer would be concrete/active Spans rather than "noop" span, hence we
            check and clean the context so it does become "noop".
            */
            if (parentSpan.isNoop()) {
                LOG.trace("Executing traced runnable - trace is noop");
                checkContextIsClean();
                runnable.run();
            }
            /*
            Thanks to the threadId we can see if this is executed on the same thread (+ we check that it's still
            the same trace) and so we can just continue with the current Thread's context.
            */
            else if (ThreadIdentifierObject.isFromCurrentThread(threadId) && parentSpan.isFromCurrentTrace()) {
                LOG.trace("Executing traced runnable - continuing trace on same thread");
                //noinspection unused
                try (final Span childSpan = startTracedChildSpan(childSpanName, parentSpan)) {
                    runnable.run();
                }
            }
            /*
            We are on a different thread and set up its thread-local context and therefore start the trace on the new
            thread.

            The other case that leads here (see previous if condition) is that execution is on a different trace but
            same thread, i.e. trace id of parentSpan does not match currently active span.
            For example, the execution of the returned runnable could be delayed to a point where the previous trace
            has ended (in which case we wanna startTrace and continue the trace with the new child span).
            Additionally, if there is already a new active trace we clear that one and notify the user that they mixed
            traces (startTrace will log if context is not clean).
            */
            else {
                LOG.trace("Executing traced runnable - continuing trace on different thread");
                startTrace(
                    factory.createBuilder()
                        .setSpanName(childSpanName)
                        .setServiceName(parentSpan.getServiceName())
                        .setParentContext(parentSpan.getTraceContext())
                        .build()
                );
                try {
                    runnable.run();
                } finally {
                    endTrace();
                }
            }
        };
    }

    /**
     * Wraps the supplied Callable so that the currently active trace can be continued within it even if executed
     * asynchronously in a different thread.
     * The child span's lifetime is therefore not tied to the lifetime of the parent.
     * <p>
     * If the returned Callable is executed on another thread it initializes the tracer's thread-local context for that
     * thread, while leaving the current thread's context unaffected.
     * <p>
     * If run on the same thread while the current trace is still active this will simply start a new child span within
     * the current thread's context.
     *
     * <h1>Example</h1>
     * <pre>
     * Future{@code <Response>} responseFuture =
     *      executor.submit(tracer.traceCallable("http-call", (){@code ->} httpClient.get(url)));
     * </pre>
     *
     * @param childSpanName name of the child Span created for the Callable - must not be null or empty.
     * @param callable      to wrap - must not be null.
     * @param <T>           the return type.
     * @return a traced Callable.
     * @throws IllegalArgumentException if arguments are null or empty.
     */
    public <T> Callable<T> traceCallable(final String childSpanName, final Callable<? extends T> callable) {
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");
        Assert.notNull(callable, "Validation failed: callable must not be null");

        /* See traceRunnable for a fully commented version of this method. */
        final TracerSpan activeSpan = getActiveSpan();
        final ThreadIdentifierObject threadId = ThreadIdentifierObject.getCurrentThreadId();
        //noinspection OverlyLongLambda
        return () -> {
            if (activeSpan.isNoop()) {
                LOG.trace("Executing traced runnable - trace is noop");
                checkContextIsClean();
                return callable.call();
            } else if (ThreadIdentifierObject.isFromCurrentThread(threadId) && activeSpan.isFromCurrentTrace()) {
                LOG.trace("Executing traced runnable - continuing trace on same thread");
                //noinspection unused
                try (final Span childSpan = startTracedChildSpan(childSpanName, activeSpan)) {
                    return callable.call();
                }
            } else {
                LOG.trace("Executing traced runnable - continuing trace on different thread");
                startTrace(
                    factory.createBuilder()
                        .setSpanName(childSpanName)
                        .setServiceName(activeSpan.getServiceName())
                        .setParentContext(activeSpan.getTraceContext())
                        .build()
                );
                try {
                    return callable.call();
                } finally {
                    endTrace();
                }
            }
        };
    }

    /**
     * Wraps the supplied Supplier so that the currently active trace can be continued within it even if executed
     * asynchronously in a different thread.
     * The child span's lifetime is therefore not tied to the lifetime of the parent.
     * <p>
     * If the returned Supplier is executed on another thread it initializes the tracer's thread-local context for that
     * thread, while leaving the current thread's context unaffected.
     * <p>
     * If run on the same thread while the current trace is still active this will simply start a new child span within
     * the current thread's context.
     *
     * <h1>Example</h1>
     * <pre>
     * CompletableFuture.supplyAsync(tracer.traceSupplier("http-call", (){@code ->} httpClient.get(url)))
     * </pre>
     *
     * @param childSpanName name of the child Span created for the Supplier - must not be null or empty.
     * @param supplier      to wrap - must not be null.
     * @param <T>           the return type.
     * @return a traced Supplier.
     * @throws IllegalArgumentException if arguments are null or empty.
     */
    public <T> Supplier<T> traceSupplier(final String childSpanName, final Supplier<? extends T> supplier) {
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");
        Assert.notNull(supplier, "Validation failed: supplier must not be null");

        /* See traceRunnable for a fully commented version of this method. */
        final TracerSpan activeSpan = getActiveSpan();
        final ThreadIdentifierObject threadId = ThreadIdentifierObject.getCurrentThreadId();
        //noinspection OverlyLongLambda
        return () -> {
            if (activeSpan.isNoop()) {
                LOG.trace("Executing traced runnable - trace is noop");
                checkContextIsClean();
                return supplier.get();
            } else if (ThreadIdentifierObject.isFromCurrentThread(threadId) && activeSpan.isFromCurrentTrace()) {
                LOG.trace("Executing traced runnable - continuing trace on same thread");
                //noinspection unused
                try (final Span childSpan = startTracedChildSpan(childSpanName, activeSpan)) {
                    return supplier.get();
                }
            } else {
                LOG.trace("Executing traced runnable - continuing trace on different thread");
                startTrace(
                    factory.createBuilder()
                        .setSpanName(childSpanName)
                        .setServiceName(activeSpan.getServiceName())
                        .setParentContext(activeSpan.getTraceContext())
                        .build());
                try {
                    return supplier.get();
                } finally {
                    endTrace();
                }
            }
        };
    }

    /**
     * Wraps the supplied Function so that the currently active trace can be continued within it even if executed
     * asynchronously in a different thread.
     * The child span's lifetime is therefore not tied to the lifetime of the parent.
     * <p>
     * If the returned Function is executed on another thread it initializes the tracer's thread-local context for that
     * thread, while leaving the current thread's context unaffected.
     * <p>
     * If run on the same thread while the current trace is still active this will simply start a new child span within
     * the current thread's context.
     *
     * <h1>Example</h1>
     * <pre>
     * completableFuture.thenApply(tracer.traceFunction("handle-response", Converter::convertResponse));
     * </pre>
     *
     * @param childSpanName name of the child Span created for the Function - must not be null or empty.
     * @param function      to wrap - must not be null.
     * @param <T>           the parameter type.
     * @param <R>           the return type.
     * @return a traced Function.
     * @throws IllegalArgumentException if arguments are null or empty.
     */
    public <T, R> Function<T, R> traceFunction(final String childSpanName,
                                               final Function<? super T, ? extends R> function) {
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");
        Assert.notNull(function, "Validation failed: function must not be null");

        /* See traceRunnable for a fully commented version of this method. */
        final TracerSpan activeSpan = getActiveSpan();
        final ThreadIdentifierObject threadId = ThreadIdentifierObject.getCurrentThreadId();
        //noinspection OverlyLongLambda
        return (input) -> {
            if (activeSpan.isNoop()) {
                LOG.trace("Executing traced runnable - trace is noop");
                checkContextIsClean();
                return function.apply(input);
            } else if (ThreadIdentifierObject.isFromCurrentThread(threadId) && activeSpan.isFromCurrentTrace()) {
                LOG.trace("Executing traced runnable - continuing trace on same thread");
                //noinspection unused
                try (final Span childSpan = startTracedChildSpan(childSpanName, activeSpan)) {
                    return function.apply(input);
                }
            } else {
                LOG.trace("Executing traced runnable - continuing trace on different thread");
                startTrace(
                    factory.createBuilder()
                        .setSpanName(childSpanName)
                        .setServiceName(activeSpan.getServiceName())
                        .setParentContext(activeSpan.getTraceContext())
                        .build()
                );
                try {
                    return function.apply(input);
                } finally {
                    endTrace();
                }
            }
        };
    }

    /**
     * Wraps the supplied Consumer so that the currently active trace can be continued within it even if executed
     * asynchronously in a different thread.
     * The child span's lifetime is therefore not tied to the lifetime of the parent.
     * <p>
     * If the returned Consumer is executed on another thread it initializes the tracer's thread-local context for that
     * thread, while leaving the current thread's context unaffected.
     * <p>
     * If run on the same thread while the current trace is still active this will simply start a new child span within
     * the current thread's context.
     *
     * <h1>Example</h1>
     * <pre>
     * completableFuture.thenAccept(
     *      tracer.traceConsumer("handle-response", (response){@code ->} handleResponse(response)));
     * </pre>
     *
     * @param childSpanName name of the child Span created for the Consumer - must not be null or empty.
     * @param consumer      to wrap - must not be null.
     * @param <T>           the return type.
     * @return a traced Consumer.
     * @throws IllegalArgumentException if arguments are null or empty.
     */
    public <T> Consumer<T> traceConsumer(final String childSpanName, final Consumer<? super T> consumer) {
        Assert.notEmpty(childSpanName, "Validation failed: childSpanName must not be null or empty");
        Assert.notNull(consumer, "Validation failed: consumer must not be null");

        /* See traceRunnable for a fully commented version of this method. */
        final TracerSpan activeSpan = getActiveSpan();
        final ThreadIdentifierObject threadId = ThreadIdentifierObject.getCurrentThreadId();
        //noinspection OverlyLongLambda
        return (input) -> {
            if (activeSpan.isNoop()) {
                LOG.trace("Executing traced runnable - trace is noop");
                checkContextIsClean();
                consumer.accept(input);
            } else if (ThreadIdentifierObject.isFromCurrentThread(threadId) && activeSpan.isFromCurrentTrace()) {
                LOG.trace("Executing traced runnable - continuing trace on same thread");
                //noinspection unused
                try (final Span childSpan = startTracedChildSpan(childSpanName, activeSpan)) {
                    consumer.accept(input);
                }
            } else {
                LOG.trace("Executing traced runnable - continuing trace on different thread");
                startTrace(
                    factory.createBuilder()
                        .setSpanName(childSpanName)
                        .setServiceName(activeSpan.getServiceName())
                        .setParentContext(activeSpan.getTraceContext())
                        .build()
                );
                try {
                    consumer.accept(input);
                } finally {
                    endTrace();
                }
            }
        };
    }
    //endregion

    ///////////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////////
    private void checkContextIsClean() {
        /*
        Check if the context is clean. This means #startTrace is being called on a thread where a previous trace
        is still active (spans are still on the stack). We submit such data on a best effort basis,
        even if they may be stale (and thus their duration skewed).
         */
        if (!tracingContext.isEmpty()) {
            LOG.warn("The Tracer's thread-local context: {} was expected to be clean but spans from a previous trace are " +
                     "still active. Those active spans will be submitted now and the context cleared.", tracingContext);
            final TracerSpan previousRootSpan = tracingContext.peekLast();
            previousRootSpan.addField(TraceFieldConstants.META_DIRTY_CONTEXT_FIELD, true);
            previousRootSpan.close();
        }
    }

    private Span startTracedChildSpan(final String childSpanName, final Span parentSpan) {
        final Span build = factory.createBuilderFromParent(parentSpan).setSpanName(childSpanName).build();
        final TracerSpan childSpan = new TracerSpan(build, this);
        pushActiveSpan(childSpan);
        logSpan("Starting child span", childSpan);
        return childSpan;
    }

    private void pushActiveSpan(final TracerSpan span) {
        if (span.isNoop()) return;

        tracingContext.push(span);
    }

    private void popActiveSpan() {
        tracingContext.pop();
    }

    private boolean containsSpan(final Span spanToDetach) {
        boolean containsSpan = false;
        for (final TracerSpan tracerSpan : tracingContext.get()) {
            if (hasSameSpanId(spanToDetach, tracerSpan)) {
                containsSpan = true;
                break;
            }
        }
        return containsSpan;
    }

    private boolean hasSameSpanId(final Span spanToDetach, final TracerSpan tracerSpan) {
        return tracerSpan.getSpanId().equals(spanToDetach.getSpanId());
    }

    @SuppressWarnings({"resource", "CastToConcreteClass"})
    private TracerSpan createNewTracerSpan(final Span span) {
        final TracerSpan tracerSpan;
        if (span.isNoop()) {
            tracerSpan = TracerSpan.getNoopTracerSpan();
        } else if (span instanceof TracerSpan) {
            // just in case we do get passed a tracer span, we don't want to wrap it again, instead re-wrap its delegate
            tracerSpan = new TracerSpan(((TracerSpan) span).getDelegate(), this);
        } else {
            tracerSpan = new TracerSpan(span, this);
        }
        return tracerSpan;
    }

    private void logSpan(final String message, final Span span) {
        if (LOG.isTraceEnabled() && span.isNoop()) { // only log noop spans at trace
            LOG.trace(
                "{} traceId: '{}', spanName: '{}', spanId: '{}'",
                message, span.getTraceId(), span.getSpanName(), span.getSpanId());
        }
        if (LOG.isDebugEnabled() && !span.isNoop()) { // avoid logging noops at debug level
            LOG.debug(
                "{} traceId: '{}', spanName: '{}', spanId: '{}'",
                message, span.getTraceId(), span.getSpanName(), span.getSpanId());
        }
    }
}
