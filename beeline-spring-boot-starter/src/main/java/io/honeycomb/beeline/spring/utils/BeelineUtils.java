package io.honeycomb.beeline.spring.utils;

import io.honeycomb.beeline.tracing.Span;
import org.springframework.http.HttpHeaders;

public final class BeelineUtils {
    private BeelineUtils() {
        // utils
    }

    public static void tryAddHeader(final HttpHeaders headers,
                                    final Span span,
                                    final String headerKey,
                                    final String fieldKey) {
        final String headerValue = headers.getFirst(headerKey);
        if (headerValue != null) {
            span.addField(fieldKey, headerValue);
        }
    }

    public static void tryAddField(final Span span, final String fieldName, final Object value) {
        if (value != null) {
            span.addField(fieldName, value);
        }
    }
}
