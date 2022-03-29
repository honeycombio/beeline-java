package io.honeycomb;

import io.honeycomb.beeline.DefaultBeeline;
import io.honeycomb.beeline.builder.BeelineBuilder;
import io.honeycomb.beeline.tracing.Span;

public class HelloWorld {

    public static void main(String[] args) {

        String API_KEY = System.getenv().getOrDefault("HONEYCOMB_API_KEY", "abc123");
        String SERVICE_NAME = System.getenv().getOrDefault("SERVICE_NAME", "beeline-java");

        DefaultBeeline beeline = DefaultBeeline.getInstance(new BeelineBuilder().writeKey(API_KEY), SERVICE_NAME);

        Span rootSpan = beeline.startSpan("HelloWorld");
        System.out.println("Hello World");
        rootSpan.close();
        beeline.close();
    }
}
