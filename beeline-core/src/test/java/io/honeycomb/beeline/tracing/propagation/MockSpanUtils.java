package io.honeycomb.beeline.tracing.propagation;
 
import io.honeycomb.beeline.tracing.Span;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

public class MockSpanUtils {
    /**
     * To make the fluent API easier to use in the tests
     */
    public static void stubFluentCalls(final Span mockSpan) {
        lenient().when(mockSpan.addField(anyString(), any())).thenReturn(mockSpan);
        lenient().when(mockSpan.addFields(anyMap())).thenReturn(mockSpan);
        lenient().when(mockSpan.addTraceField(anyString(), any())).thenReturn(mockSpan);
        lenient().when(mockSpan.addTraceFields(anyMap())).thenReturn(mockSpan);
        lenient().when(mockSpan.markStart()).thenReturn(mockSpan);
        lenient().when(mockSpan.markStart(anyLong(), anyLong())).thenReturn(mockSpan);
    }
 }
