package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.propagation.BeelineServletFilter;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.beeline.tracing.propagation.PropagationCodec;
import io.honeycomb.beeline.tracing.utils.PathMatcher;

import javax.servlet.FilterConfig;
import java.util.List;
import java.util.Map;

import static io.honeycomb.beeline.spring.utils.InstrumentationConstants.WEBMVC_INSTRUMENTATION_NAME;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_ASYNC_DISPATCH_FIELD;
import static io.honeycomb.beeline.spring.utils.MoreTraceFieldConstants.SPRING_DISPATCHER_TYPE_FIELD;

public class SpringServletFilter extends BeelineServletFilter implements BeelineInstrumentation {
    public SpringServletFilter(String serviceName, List<String> includePaths, List<String> excludePaths, Beeline beeline) {
        this(serviceName, includePaths, excludePaths, beeline, Propagation.honeycombHeaderV1());
    }

    public SpringServletFilter(String serviceName,
                               List<String> includePaths,
                               List<String> excludePaths,
                               Beeline beeline,
                               PropagationCodec<Map<String, String>> propagationCodec) {
        super(serviceName, beeline, includePaths, excludePaths,
            BeelineServletFilter.DEFAULT_REDISPATCH_SPAN_NAMING_FUNCTION,
            BeelineServletFilter.DEFAULT_REQUEST_SPAN_NAMING_FUNCTION,
            new SpringPathMatcherAdapter(),
            propagationCodec);
    }

    /**
     * For backwards compatibility we override the span field names.
     * <p>
     * Original users of this class will already have stored {@code spring}-namespaced fields, so we maintain that here.
     */
    @Override
    protected String getAsyncDispatchSpanFieldName() {
        return SPRING_ASYNC_DISPATCH_FIELD;
    }

    /**
     * For backwards compatibility we override the span field names.
     * <p>
     * Original users of this class will already have stored {@code spring}-namespaced fields, so we maintain that here.
     */
    @Override
    protected String getDispatcherTypeSpanFieldName() {
        return SPRING_DISPATCHER_TYPE_FIELD;
    }

    @Override
    public String getName() {
        return WEBMVC_INSTRUMENTATION_NAME;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        // no init needed
    }

    @Override
    public void destroy() {
        // no destroy needed
    }

    /**
     * Use Spring's actual {@link org.springframework.util.AntPathMatcher} as it will be available on the
     * classpath.
     */
    private static class SpringPathMatcherAdapter implements PathMatcher {
        private final org.springframework.util.PathMatcher delegate = new org.springframework.util.AntPathMatcher();

        @Override
        public boolean match(String pattern, String path) {
            return delegate.match(pattern, path);
        }
    }
}
