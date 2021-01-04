package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.builder.BeelineBuilder;
import io.honeycomb.beeline.spring.autoconfig.BeelineProperties;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import io.honeycomb.libhoney.eventdata.ResolvedEvent;
import io.honeycomb.libhoney.transport.Transport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import zipkin2.Span;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
public class BraveBeelineReporterTest {
    private static final long SPAN_TIMESTAMP_NANOS = 324234L;
    private static final long SPAN_TIMESTAMP_MILLIS = SPAN_TIMESTAMP_NANOS / 1000;
    private static final long TIMESTAMP_AFTER_SPAN = SPAN_TIMESTAMP_MILLIS + 1;

    @Mock
    BeelineProperties properties;
    @Mock
    Transport mockTransport;
    @Captor
    ArgumentCaptor<?> captor;

    Beeline beeline;
    BraveBeelineReporter reporter;
    Span.Builder spanBuilder;

    @Before
    public void setUp() {
        when(properties.getServiceName()).thenReturn("BraveBeelineReporterTest");
        when(mockTransport.submit(any())).thenReturn(true);
        final BeelineBuilder beelineBuilder = new BeelineBuilder();
        beeline = spy(beelineBuilder.transport(mockTransport).writeKey("testKey").dataSet("testSet").build());
        reporter = new BraveBeelineReporter(beeline, properties);
        spanBuilder = Span.newBuilder().name("testSpan").traceId("abcdef0123").id(1234L).parentId(5678L).timestamp(SPAN_TIMESTAMP_NANOS);
    }

    @Test
    public void GIVEN_span_EXEPECT_event() {
        reporter.report(spanBuilder.build());
        verify(mockTransport, (times(1))).submit((ResolvedEvent) this.captor.capture());
        verifyNoMoreInteractions(mockTransport);

        final ResolvedEvent captured = (ResolvedEvent) captor.getValue();
        Assert.assertEquals("testKey", captured.getWriteKey());
        Assert.assertEquals("testSet", captured.getDataset());

        final Map<String, Object> fields = captured.getFields();
        Assert.assertEquals("testspan", fields.get(TraceFieldConstants.SPAN_NAME_FIELD)); // note: zipkin span names are automatically lower-cased
        Assert.assertEquals("000000abcdef0123", fields.get(TraceFieldConstants.TRACE_ID_FIELD)); // trace IDs are padded to 16 chars
        Assert.assertEquals("00000000000004d2", fields.get(TraceFieldConstants.SPAN_ID_FIELD)); // span IDs are converted to hex and padded to 16 chars
        Assert.assertEquals("000000000000162e", fields.get(TraceFieldConstants.PARENT_ID_FIELD)); // parent span IDs are converted to hex and padded to 16 chars
    }

    @Test
    public void GIVEN_spanWithAnnotation_EXPECT_rootEventAndEventPerAnnotation() {
        final Span span = spanBuilder.addAnnotation(TIMESTAMP_AFTER_SPAN, "value")
            .addAnnotation(TIMESTAMP_AFTER_SPAN + 1, "value2")
            .build();
        reporter.report(span);
        verify(mockTransport, (times(3))).submit((ResolvedEvent) this.captor.capture());
        verifyNoMoreInteractions(mockTransport);
        final List<ResolvedEvent> capturedValues = (List<ResolvedEvent>) captor.getAllValues();
        Assert.assertEquals("Expected 3 events (1 event + 2 span events)", capturedValues.size(), 3);
        final Set<String> foundAnnotationValues = getAnnotations(capturedValues);
        Assert.assertEquals("Expected to find 1 root event and 2 annotations", 3, foundAnnotationValues.size());
        Assert.assertEquals("Expected found root event and annotations to match", new HashSet<>(Arrays.asList("value", "rootSpan", "value2")), foundAnnotationValues);

    }

    private Set<String> getAnnotations(final List<ResolvedEvent> capturedValues) {
        final Set<String> foundAnnotationValues = new HashSet<>();
        for (ResolvedEvent event : capturedValues) {
            final long timestamp = event.getTimestamp();
            if (timestamp == SPAN_TIMESTAMP_MILLIS) {
                foundAnnotationValues.add("rootSpan");
            } else if (timestamp > SPAN_TIMESTAMP_MILLIS) {
                final Map<String, Object> fields = event.getFields();
                foundAnnotationValues.add((String) fields.get(TraceFieldConstants.SPAN_NAME_FIELD));
            } else {
                foundAnnotationValues.add("unknown event");
            }
        }
        return foundAnnotationValues;
    }

    @Test
    public void GIVEN_spanWithTags_EXPECT_eventSentWithTagsAsFieldsOnEvent() {
        final Span span = spanBuilder.putTag("field", "value").build();
        reporter.report(span);
        verify(mockTransport, times(1)).submit((ResolvedEvent) captor.capture());
        final Object captured = captor.getValue();
        Assert.assertTrue("expected submission to be a ResolvedEvent", ResolvedEvent.class.isAssignableFrom(captured.getClass()));
        final ResolvedEvent actualEvent = (ResolvedEvent) captured;
        final boolean foundTagMatch = searchForExactTag(Collections.singletonList(actualEvent), "field", "value");
        Assert.assertTrue("Expected event fields to contain tag", foundTagMatch);
        verifyNoMoreInteractions(mockTransport);
    }

    private boolean searchForExactTag(final List<ResolvedEvent> capturedValues, final String tagName, final String value) {
        for (ResolvedEvent event : capturedValues) {
            final Map<String, Object> fields = event.getFields();
            if (!fields.containsKey(tagName)) {
                continue;
            }
            if (value.equals(fields.get(tagName))) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void GIVEN_spanWithDuration_EXPECT_eventWithDuration() {
        final Span span = spanBuilder.duration(494L).build(); // 0.494 millis in micros
        reporter.report(span);
        verify(mockTransport, times(1)).submit((ResolvedEvent) captor.capture());
        final Object captured = captor.getValue();
        Assert.assertTrue("expected submission to be a ResolvedEvent", ResolvedEvent.class.isAssignableFrom(captured.getClass()));
        final ResolvedEvent event = (ResolvedEvent) captured;
        final Map<String, Object> actualFields = event.getFields();
        Assert.assertTrue("Expected duration on event", actualFields.containsKey(TraceFieldConstants.DURATION_FIELD));
        Assert.assertEquals("0.494", String.valueOf(actualFields.get(TraceFieldConstants.DURATION_FIELD)));
    }

    @Test
    public void GIVEN_spanWithAnnotationNotSampled_EXPECT_noAnnotationSent() {
        // create mocks
        final SpanPostProcessor postProcessor = mock(SpanPostProcessor.class);
        final SpanBuilderFactory factory = mock(SpanBuilderFactory.class);
        final SpanBuilderFactory.SpanBuilder spanBuilder = mock(SpanBuilderFactory.SpanBuilder.class);
        final io.honeycomb.beeline.tracing.Span mockSpan = mock(io.honeycomb.beeline.tracing.Span.class);
        doReturn("000000000000000001").when(mockSpan).getSpanId();

        //configure mocks
        doReturn(factory).when(beeline).getSpanBuilderFactory();
        doReturn(spanBuilder).when(factory).createBuilderFromParent(any());
        doReturn(spanBuilder).when(factory).createBuilder();
        doReturn(postProcessor).when(spanBuilder).getProcessor();
        doReturn(spanBuilder).when(spanBuilder).setSpanId(any());
        doReturn(spanBuilder).when(spanBuilder).setSpanName(any());
        doReturn(spanBuilder).when(spanBuilder).setServiceName(any());
        doReturn(spanBuilder).when(spanBuilder).setParentContext(any());
        doReturn(mockSpan).when(spanBuilder).build();
        doReturn(0).when(postProcessor).runSamplerHook(any());
        doReturn(mockSpan).when(beeline).startTrace(anyString(), any(PropagationContext.class), anyString());

        // execute test
        final Span span = this.spanBuilder.addAnnotation(TIMESTAMP_AFTER_SPAN, "value")
            .build();
        reporter.report(span);

        // verify
        verifyNoMoreInteractions(mockTransport);
    }

    @Test
    public void GIVEN_spanWithTagAndAnnotation_EXPECT_eventWithFieldDataAndAlsoSpanEvents() {
        // setup
        final Span span = spanBuilder.addAnnotation(TIMESTAMP_AFTER_SPAN, "aValue")
            .putTag("field1", "value2")
            .build();

        // execute test
        reporter.report(span);

        // verify mocks
        verify(mockTransport, (times(2))).submit((ResolvedEvent) this.captor.capture());
        verifyNoMoreInteractions(mockTransport);

        // verify result
        final List<ResolvedEvent> capturedValues = (List<ResolvedEvent>) captor.getAllValues();
        Assert.assertEquals("Expected 2 events (1 event + 1 span events)", capturedValues.size(), 2);
        final Set<String> foundAnnotationValues = getAnnotations(capturedValues);
        Assert.assertEquals("Expected to find 1 root event and 1 span event", 2, foundAnnotationValues.size());
        Assert.assertEquals("Expected list of annotations to match", new HashSet<>(Arrays.asList("aValue", "rootSpan")), foundAnnotationValues);

        final boolean foundTagMatch = searchForExactTag(capturedValues, "field1", "value2");
        Assert.assertTrue("Missing tag or non-matching tag value", foundTagMatch);
    }
}
