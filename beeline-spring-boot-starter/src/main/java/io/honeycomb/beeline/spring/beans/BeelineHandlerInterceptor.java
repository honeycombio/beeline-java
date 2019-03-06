package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.libhoney.utils.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_HANDLER_CLASS_FIELD;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_HANDLER_METHOD_FIELD;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_MATCHED_PATTERN_FIELD;


/**
 * The interceptor is coupler to the {@link BeelineServletFilter} and requires it to have set up the thread local
 * context via the Tracer.
 * <p>
 * The purpose of this is to add additional fields to the web application's "root" Span that are not yet available
 * within a Servlet Filter. Namely, information about the handler mapping.
 * <p>
 * For details about the execution flow see the Javadoc of the super classes and the {@link BeelineServletFilter}.
 */
public class BeelineHandlerInterceptor extends HandlerInterceptorAdapter {
    private final Tracer tracer;
    private final SpanBuilderFactory factory;

    public BeelineHandlerInterceptor(final Tracer tracer, final SpanBuilderFactory factory) {
        Assert.notNull(tracer, "Validation failed: tracer must not be null");
        Assert.notNull(factory, "Validation failed: factory must not be null");

        this.factory = factory;
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request,
                             final HttpServletResponse response,
                             final Object handler) {
        /*
        We skip when it's an ASYNC dispatch, as the interceptor would have already been called previously
        with normal dispatch, so we already have the data we need.
        */
        if (request.getDispatcherType() != DispatcherType.ASYNC) {
            /*
            The handler is a HandlerMethod if the request was mapped to a Spring controller method.
             */
            if (handler instanceof HandlerMethod) {
                addHandlerMethodFields((HandlerMethod) handler);
            }
            addHandlerType(handler);
            tryExtractMatchedPattern(request);
        }
        return true;
    }

    private void addHandlerMethodFields(final HandlerMethod handler) {
        final String newSpanName = deriveHandlerName(handler);
        final String handlerName = deriveHandlerMethodValue(handler);
        setCurrentSpanName(newSpanName);
        tracer.getActiveSpan().addField(SPRING_HANDLER_METHOD_FIELD, handlerName);
    }

    private void setCurrentSpanName(final String newSpanName) {
        final Span detached = tracer.popSpan(tracer.getActiveSpan());
        final Span copyWithNewName = factory.createBuilderFrom(detached)
            .setSpanName(newSpanName)
            .build();
        tracer.pushSpan(copyWithNewName);
    }

    private void addHandlerType(final Object handler) {
        tracer.getActiveSpan().addField(SPRING_HANDLER_CLASS_FIELD, handler.getClass().getSimpleName());
    }

    private void tryExtractMatchedPattern(final HttpServletRequest request) {
        final Object patternMatched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (patternMatched != null) {
            tracer.getActiveSpan().addField(SPRING_MATCHED_PATTERN_FIELD, patternMatched.toString());
        }
    }

    private String deriveHandlerMethodValue(final HandlerMethod method) {
        return method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
    }

    private String deriveHandlerName(final HandlerMethod method) {
        return StringUtils.capitalize(method.getMethod().getName());
    }
}
