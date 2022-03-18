package io.honeycomb.beeline.spring.autoconfig;

import io.honeycomb.beeline.spring.beans.BeelineHandlerInterceptor;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.libhoney.LibHoney;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnClass({LibHoney.class, Tracing.class})
@EnableConfigurationProperties(BeelineProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "honeycomb.beeline.enabled", matchIfMissing = true)
@Import(BeelineConfig.class)
public class BeelineAutoconfig implements WebMvcConfigurer {

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private BeelineHandlerInterceptor beelineHandlerInterceptor;

    @Autowired
    private BeelineProperties properties;

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        final InterceptorRegistration interceptorRegistration = registry.addInterceptor(beelineHandlerInterceptor);
        final List<String> includePaths = properties.getIncludePathPatterns();
        final List<String> excludePaths = properties.getExcludePathPatterns();
        if (!includePaths.isEmpty()) {
            interceptorRegistration.addPathPatterns(includePaths);
        }
        if (!excludePaths.isEmpty()) {
            interceptorRegistration.excludePathPatterns(excludePaths);
        }
    }
}
