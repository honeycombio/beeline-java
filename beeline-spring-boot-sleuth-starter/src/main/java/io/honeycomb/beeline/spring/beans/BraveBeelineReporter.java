package io.honeycomb.beeline.spring.beans;


import brave.propagation.ExtraFieldPropagation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.honeycomb.beeline.spring.autoconfig.BeelineProperties;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * This class bridges the Brave Framework and the Beeline Framework. It uses zipkin2.reporter.Reporter interface over
 * zipkin2.reporter.Sender as Sender functionality is already handled by Honeycomb's Transport.
 * @see zipkin2.reporter.Reporter
 * @see zipkin2.reporter.Sender
 * @see io.honeycomb.libhoney.transport.Transport
 */
public class BraveBeelineReporter implements Reporter<Span> {

    private final Beeline beeline;
    private final BeelineProperties properties;

    public BraveBeelineReporter(final Beeline beeline, final BeelineProperties properties) {
        this.beeline = beeline;
        this.properties = properties;
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE") // Java 11 false positive
    @Override
    public void report(final Span span) {
        try (io.honeycomb.beeline.tracing.Span hcRootSpan = transformBraveSpanToHoneycombSpan(span)) {
            adaptBraveModelToHoneycombModel(span, hcRootSpan);
        }
    }

    private void adaptBraveModelToHoneycombModel(final Span span, final io.honeycomb.beeline.tracing.Span hcRootSpan) {
        final long startTime = span.timestampAsLong();
        if (startTime > 0) {
            // Brave uses 0 for no timestamp while Honeycomb uses -1L
            hcRootSpan.markStart(startTime, startTime);
        }
        adaptBraveAnnotationToHoneycombSpanEvent(span, hcRootSpan);
        ExtraFieldPropagation.getAll().forEach(hcRootSpan::addField);
        span.tags().forEach(hcRootSpan::addField);
    }

    private void adaptBraveAnnotationToHoneycombSpanEvent(final Span span, final io.honeycomb.beeline.tracing.Span hcRootSpan) {
        final SpanBuilderFactory.SpanBuilder spanBuilder = beeline.getSpanBuilderFactory().createBuilderFromParent(hcRootSpan);
        if (spanBuilder.getProcessor().runSamplerHook(hcRootSpan) <= 0) {
            return;
        }
        // submit annotation spans if hcRootSpan should be sampled (consider annotations a bundle with the span)
        span.annotations().forEach(annotation -> {
            // potentially add configuration property and parallelize this annotation processing
            spanBuilder.setSpanName(annotation.value());
            final Event event = spanBuilder.getProcessor().generateSpanEvent(spanBuilder.build());
            event.setTimestamp(annotation.timestamp());
            event.sendPresampled(); // skip sampler
        });
    }

    private io.honeycomb.beeline.tracing.Span transformBraveSpanToHoneycombSpan(final Span span) {
        final io.honeycomb.beeline.tracing.Span hcRootSpan = beeline.startTrace(span.name(), PropagationContext.emptyContext(), properties.getServiceName());
        final long startTimestamp = span.timestamp();
        if (startTimestamp > 0) {
            hcRootSpan.markStart(startTimestamp, startTimestamp);
        }
        if (span.durationAsLong() > 0) {
            // Brave uses zero for no timestamp
            hcRootSpan.setDuration(span.durationAsLong());
        }
        return hcRootSpan;
    }
}
