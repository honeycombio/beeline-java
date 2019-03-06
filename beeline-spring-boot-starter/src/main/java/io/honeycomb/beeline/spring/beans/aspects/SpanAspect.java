package io.honeycomb.beeline.spring.beans.aspects;

import io.honeycomb.beeline.spring.beans.BeelineInstrumentation;
import io.honeycomb.beeline.spring.utils.BeelineUtils;
import io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.libhoney.utils.Assert;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.ANNOTATED_METHOD_TYPE;
import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.AOP_INSTRUMENTATION_NAME;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.*;
import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.TYPE_FIELD;

/**
 * An aspect that allows declarative creation of child Spans in a Beeline-instrumented Spring application.
 * <p>
 * This Aspect will apply to any method of a Spring Bean that is annotated with {@link ChildSpan} and optionally has
 * {@link SpanField} annotations on parameters.
 * <p>
 * More details on the javadoc of each annotation.
 */
@Aspect
public class SpanAspect implements BeelineInstrumentation {

    private final Tracer tracer;

    public SpanAspect(final Tracer tracer) {
        Assert.notNull(tracer, "Validation failed: tracer must not be null");

        this.tracer = tracer;
    }

    @Override
    public String getName() {
        return AOP_INSTRUMENTATION_NAME;
    }

    @Around("@annotation(span)")
    public Object around(final ProceedingJoinPoint joinPoint, final ChildSpan span) throws Throwable {
        final String spanName = determineSpanName(joinPoint, span);
        final Span child = tracer.startChildSpan(spanName);
        try {
            child
                .addField(TYPE_FIELD, ANNOTATED_METHOD_TYPE)
                .addField(JOIN_POINT_FIELD, joinPoint.getSignature().toShortString());
            addParameterFields(joinPoint, child);
            final Object result = joinPoint.proceed();
            final Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
            if (span.addResult() && returnType != void.class) {
                child.addField(JOIN_POINT_RESULT_FIELD, result);
            }
            return result;
        } catch (final Exception e) {
            child.addField(JOIN_POINT_ERROR_FIELD, e.getClass().getSimpleName());
            BeelineUtils.tryAddField(child, JOIN_POINT_ERROR_DETAIL, e.getMessage());
            throw e;
        } finally {
            child.close();
        }
    }

    protected String determineSpanName(final ProceedingJoinPoint joinPoint, final ChildSpan span) {
        final String spanName;
        if (!StringUtils.isEmpty(span.value())) {
            spanName = span.value();
        } else if (!StringUtils.isEmpty(span.name())) {
            spanName = span.name();
        } else {
            spanName = StringUtils.capitalize(joinPoint.getSignature().getName());
        }
        return spanName;
    }

    /**
     * A parameter is captured and added as a Span field if annotated with {@link SpanField}. The field name is derived
     * based on on the following order:
     * <ol>
     * <li>A user-supplied parameter name (via {@link SpanField#name()} or {@link SpanField#value()}).</li>
     * <li>With the {@value MoreTraceFieldConstants#JOIN_POINT_PARAM_FIELD_PREFIX} prefix, use the parameter name
     * as determined via reflection, which depends on the code having been compiled with the {@code -parameters} option.
     * </li>
     * <li>With the {@value MoreTraceFieldConstants#JOIN_POINT_PARAM_FIELD_PREFIX} prefix, use the parameter's
     * index within the parameters list.</li>
     * </ol>
     *
     * @param parameter       to inspect.
     * @param parameterIndex  showing position of the parameter in the list of parameters.
     * @param fieldAnnotation to inspect.
     * @return the resolved field name.
     */
    protected String determineParameterFieldName(final Parameter parameter,
                                                 final int parameterIndex,
                                                 final SpanField fieldAnnotation) {
        final String fieldName;
        //noinspection IfStatementWithTooManyBranches
        if (!StringUtils.isEmpty(fieldAnnotation.value())) {
            fieldName = fieldAnnotation.value();
        } else if (!StringUtils.isEmpty(fieldAnnotation.name())) {
            fieldName = fieldAnnotation.name();
        } else if (parameter.isNamePresent()) {
            fieldName = JOIN_POINT_PARAM_FIELD_PREFIX + parameter.getName();
        } else {
            fieldName = JOIN_POINT_PARAM_FIELD_PREFIX + parameterIndex;
        }
        return fieldName;
    }

    private void addParameterFields(final ProceedingJoinPoint joinPoint, final Span child) {
        final Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        final Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final Parameter[] params = method.getParameters();

        for (int i = 0; i < params.length; i++) {
            for (final Annotation paramAnnotation : parameterAnnotations[i]) {
                if (paramAnnotation instanceof SpanField) {
                    final SpanField fieldAnnotation = (SpanField) paramAnnotation;
                    final Parameter param = params[i];
                    final String name = determineParameterFieldName(param, i, fieldAnnotation);
                    final Object arg = args[i];

                    child.addField(name, arg);
                }
            }
        }
    }
}
