package io.honeycomb.beeline.spring.beans.aspects;

import io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * In a Beeline-instrumented application, this annotation will apply {@link SpanAspect} to a Spring bean.
 * <p>
 * It provides a declarative way for creating child spans when calling an annotated method from another class.
 * In doing so it measures the duration of the method call, captures info about the call site (such as the method name),
 * and reports on any exceptions that may have been thrown (but does not swallow it).
 * <p>
 * The child span created by the Aspect can be further customised by this annotation's parameters. Additionally, if you
 * are interested in capturing any method arguments, you can use {@link SpanField} in conjunction with this annotation.
 * By default, no arguments will be captured.
 * <p>
 * Since Spring AOP uses a proxy mechanism, this will not work when calling the method from within the same class.
 * For reference,
 * <a
 * href=
 * "https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#aop-understanding-aop-proxies">
 * "Understanding AOP Proxies</a> explains this mechanism.
 *
 * <h1>Example 1</h1>
 * <pre>
 * {@code @ChildSpan}
 *  public void doStuff({@code @SpanField} final String id,
 *                      {@code @SpanField} final String name) {
 *      // the following returns the Span created by using the @ChildSpan annotation
 *      Span childSpan = beeline.getActiveSpan();
 *      // add more contextual info in addition to the captured arguments (see @SpanField)
 *      childSpan.addField(...);
 *  }
 *  </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChildSpan {

    /**
     * Overrides the default name given to the operation covered by the child Span created by the Aspect.
     * <p>
     * If no value is provided, the Aspect will fallback to using the method name (capitalized) instead.
     * <p>
     * This is an alias for {@link #name()}.
     *
     * @return explicit name of span
     */
    @AliasFor("name")
    String value() default "";


    /**
     * Overrides the default name given to the operation covered by the child Span created by the Aspect.
     * <p>
     * If no value is provided, the Aspect will fallback to using the method name (capitalized) instead.
     * <p>
     * This is an alias for {@link #value()}.
     *
     * @return explicit name of span
     */
    @AliasFor("value")
    String name() default "";

    /**
     * If set to true, the Aspect will capture the return value and add it as a field
     * ({@value MoreTraceFieldConstants#JOIN_POINT_RESULT_FIELD}) to the Span.
     *
     * @return whether to add the result value
     */
    boolean addResult() default false;
}
