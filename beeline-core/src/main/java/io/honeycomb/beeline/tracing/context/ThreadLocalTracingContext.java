package io.honeycomb.beeline.tracing.context;

import io.honeycomb.beeline.tracing.TracerSpan;

import java.util.ArrayDeque;
import java.util.Deque;

public class ThreadLocalTracingContext
    implements TracingContext
{
    /*
    Usually, thread-locals are made static to make data globally accessible to a thread in a thread-safe/efficient
    manner (e.g. static calls into an MDC context).

    However, Tracer maintains thread local state per thread & per instance. This is because all access to the class is
    done non-statically and state is managed through the instance. While an unlikely situation, this makes it at least
    possible for users to create multiple Tracer instances with different configurations (e.g. sending to different
    data-sets) without the Tracers interfering with each other's thread-local context.
    */
    private final ThreadLocal<Deque<TracerSpan>> spanStack;

    public ThreadLocalTracingContext(){
        this.spanStack = ThreadLocal.withInitial( ArrayDeque::new);
    }

    @Override
    public Deque<TracerSpan> get()
    {
        return spanStack.get();
    }

    @Override
    public int size()
    {
        return spanStack.get().size();
    }

    @Override
    public TracerSpan peekLast()
    {
        return spanStack.get().peekLast();
    }

    @Override
    public TracerSpan peekFirst()
    {
        return spanStack.get().peekFirst();
    }

    @Override
    public boolean isEmpty()
    {
        return spanStack.get().isEmpty();
    }

    @Override
    public void push( final TracerSpan span )
    {
        spanStack.get().push( span );
    }

    @Override
    public TracerSpan pop()
    {
        return spanStack.get().pop();
    }
}
