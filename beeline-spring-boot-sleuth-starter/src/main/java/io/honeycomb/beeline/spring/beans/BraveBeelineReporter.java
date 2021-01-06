package io.honeycomb.beeline.spring.beans;


import brave.propagation.ExtraFieldPropagation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.honeycomb.beeline.spring.autoconfig.BeelineProperties;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.libhoney.Event;
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

    private static final Short MICROS_IN_MILLISECOND = 1000;

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
        final PropagationContext propagationContext = new PropagationContext(span.traceId(), span.parentId(), null, null);
        final io.honeycomb.beeline.tracing.Span hcRootSpan = beeline.getTracer().startTrace(
            beeline.getSpanBuilderFactory().createBuilder()
                .setSpanName(span.name())
                .setServiceName(properties.getServiceName())
                .setParentContext(propagationContext)
                .setSpanId(span.id())
                .build()
        );
        final Long startTimestamp = span.timestamp();
        if (startTimestamp != null && startTimestamp > 0) {
            hcRootSpan.markStart(startTimestamp/MICROS_IN_MILLISECOND, startTimestamp/MICROS_IN_MILLISECOND);
        }
        if (span.durationAsLong() > 0) {
            // Brave uses zero for no timestamp
            hcRootSpan.setDuration((double) span.durationAsLong() / MICROS_IN_MILLISECOND);
        }
        return hcRootSpan;
    }
}
