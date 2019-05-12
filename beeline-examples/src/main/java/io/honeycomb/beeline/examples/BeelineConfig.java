package io.honeycomb.beeline.examples;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.sampling.Sampling;

import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.Options;
import io.honeycomb.libhoney.responses.ResponseObservable;
import io.honeycomb.libhoney.transport.impl.ConsoleTransport;

public class BeelineConfig {
    private static final String DEFAULT_WRITE_KEY = "test-write-key";
    private static final String DEFAULT_DATASET = "test-dataset";

    private static final HoneyClient client;
    private static final Beeline beeline;

    static {
        String wk = System.getProperty("io.honeycomb.beeline.write-key", DEFAULT_WRITE_KEY);
        String ds = System.getProperty("io.honeycomb.beeline.dataset", DEFAULT_DATASET);
        Options options                 = LibHoney.options().setDataset(ds).setWriteKey(wk).build();
        if (wk.equals(DEFAULT_WRITE_KEY) && ds.equals(DEFAULT_DATASET)) {
            client                      = new HoneyClient(options, new ConsoleTransport(new ResponseObservable()));
        } else {
            client                      = LibHoney.create(options);
        }
        SpanPostProcessor postProcessor = Tracing.createSpanProcessor(client, Sampling.alwaysSampler());
        SpanBuilderFactory factory      = Tracing.createSpanBuilderFactory(postProcessor, Sampling.alwaysSampler());
        Tracer tracer                   = Tracing.createTracer(factory);
        beeline                         = Tracing.createBeeline(tracer, factory);
    }

    public Beeline getBeeline() {
        return beeline;
    }

		public void shutdown() {
			client.close();
		}
}
