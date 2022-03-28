package io.honeycomb;

import io.honeycomb.beeline.DefaultBeeline;
import io.honeycomb.beeline.tracing.Span;

public class HelloWorldClassic {

    public static void main(String[] args) {

        String API_KEY = System.getenv().getOrDefault("HONEYCOMB_API_KEY", "abc123");
        String DATASET = System.getenv().getOrDefault("HONEYCOMB_DATASET", "beeline-java");
        String SERVICE_NAME = System.getenv().getOrDefault("SERVICE_NAME", "beeline-java");

        DefaultBeeline beeline = DefaultBeeline.getInstance(DATASET, SERVICE_NAME, API_KEY);

        Span rootSpan = beeline.startSpan("HelloWorldClassic");
        System.out.println("Hello World");
        rootSpan.close();
        beeline.close();
    }
}
