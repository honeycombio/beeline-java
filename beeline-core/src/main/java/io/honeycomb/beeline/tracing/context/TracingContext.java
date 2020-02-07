package io.honeycomb.beeline.tracing.context;

import io.honeycomb.beeline.tracing.TracerSpan;

import java.util.Deque;

public interface TracingContext
{
    Deque<TracerSpan> get();

    int size();

    TracerSpan peekLast();

    TracerSpan peekFirst();

    boolean isEmpty();

    void push( TracerSpan span );

    TracerSpan pop();
}
