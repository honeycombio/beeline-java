package io.honeycomb.beeline.spring.beans.aspects;

import io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This provides a declarative way to capture specific method arguments and add them as Span fields when used in
 * conjunction with {@link ChildSpan}. By default the name of the Span field will be prefixed by
 * {@link MoreTraceFieldConstants#JOIN_POINT_PARAM_FIELD_PREFIX} and either uses the parameter index, or parameter name
 * as the suffix - depending on whether the {@code -parameters} compiler option is enabled.
 * <p>
 * Otherwise, you can provide an explicit name via this annotation's parameters.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpanField {
    /**
     * Overrides the default field name for the annotated parameter.
     * <p>
     * This is an alias for {@link #name()}.
     *
     * @return explicit span field name.
     */
    @AliasFor("name")
    String value() default "";

    /**
     * Overrides the default field name for the annotated parameter.
     * <p>
     * This is an alias for {@link #value()}.
     *
     * @return explicit span field name.
     */
    @AliasFor("value")
    String name() default "";
}
