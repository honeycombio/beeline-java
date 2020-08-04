package io.honeycomb.beeline.spring.autoconfig;

import io.honeycomb.beeline.spring.beans.BeelineHandlerInterceptor;
import io.honeycomb.beeline.spring.beans.BeelineQueryListenerForJDBC;
import io.honeycomb.beeline.spring.beans.BeelineRestTemplateInterceptor;
import io.honeycomb.beeline.spring.beans.DataSourceProxyBeanPostProcessor;
import io.honeycomb.beeline.spring.beans.DebugResponseObserver;
import io.honeycomb.beeline.spring.beans.SpringServletFilter;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.ResponseObserver;
import io.honeycomb.libhoney.transport.impl.BatchingHttpTransport;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


/**
 * This is testing the Autoconfiguration classes as explained here
 * https://spring.io/blog/2018/03/07/testing-auto-configurations-with-spring-boot-2-0
 */
public class BeelineAutoconfigTest {

    private ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    private WebApplicationContextRunner webApplicationContextRunner = new WebApplicationContextRunner();

    private final List<Class<?>> coreBeans = Arrays.asList(
        HoneyClient.class,
        Tracer.class,
        TraceSampler.class,
        SpringServletFilter.class,
        BeelineMetaFieldProvider.class,
        Beeline.class,
        BeelineHandlerInterceptor.class,
        BeelineRestTemplateInterceptor.class,
        BatchingHttpTransport.class,
        DataSourceProxyBeanPostProcessor.class,
        BeelineQueryListenerForJDBC.class
    );

    private final String[] defaultProps = {
        "honeycomb.beeline.write-key=someKey",
        "honeycomb.beeline.dataset=someData",
        "honeycomb.beeline.enabled=true",
        "honeycomb.beeline.service-name=someService"
    };

    @Test
    public void GIVEN_aWebApplicationContext_EXPECT_AllCoreBeansToLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> coreBeans.forEach(beanClass -> assertThat(context).hasSingleBean(beanClass)));
    }

    @Test
    public void GIVEN_variousPropertyValues_EXPECT_ConfigurationPropertiesToHaveInitialisedCorrectly() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(
                "honeycomb.beeline.write-key=someKey0123",
                "honeycomb.beeline.dataset=someData123",
                "honeycomb.beeline.api-host=http://localhost:9111/events",
                "honeycomb.beeline.service-name=TestService")

            .run(context -> {
                assertThat(context.getBean(BeelineProperties.class).getDataset()).isEqualTo("someData123");
                assertThat(context.getBean(BeelineProperties.class).getWriteKey()).isEqualTo("someKey0123");
                assertThat(Objects.requireNonNull(context.getBean(BeelineProperties.class).getApiHost()).toString()).isEqualTo("http://localhost:9111/events");
                assertThat(context.getBean(BeelineProperties.class).getServiceName()).isEqualTo("TestService");
                assertThat(context.getBean(BeelineProperties.class).isEnabled()).isTrue();
            });
    }

    @Test
    public void GIVEN_noConfigureServiceName_EXPECT_FallbackToSpringApplicationName() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(
                "honeycomb.beeline.write-key=someKey",
                "honeycomb.beeline.dataset=someData",
                "honeycomb.beeline.jdbc.enabled=false")
            .withPropertyValues("spring.application.name=TestApp")

            .run(context -> assertThat(context.getBean(BeelineProperties.class).getServiceName()).isEqualTo("TestApp"));
    }

    @Test
    public void GIVEN_pathPatternsDefinedAsPropertyList_EXPECT_patternsToBeSeparateItems() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues(
                "honeycomb.beeline.exclude-path-patterns=/pattern1,/pattern2",
                "honeycomb.beeline.include-path-patterns=/pattern3,/pattern4")

            .run(context -> {
                assertThat(context.getBean(BeelineProperties.class).getExcludePathPatterns()).contains("/pattern1", "/pattern2");
                assertThat(context.getBean(BeelineProperties.class).getIncludePathPatterns()).contains("/pattern3", "/pattern4");
            });
    }

    @Test
    public void GIVEN_beelineIsEnabledExplicitly_EXPECT_AllCoreBeansToLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> coreBeans.forEach(beanClass -> assertThat(context).hasSingleBean(beanClass)));
    }

    @Test
    public void GIVEN_beelineIsDisabledExplicitly_EXPECT_NoCoreBeansToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.enabled=false")

            .run(context -> coreBeans.forEach(beanClass -> assertThat(context).doesNotHaveBean(beanClass)));
    }

    @Test
    public void GIVEN_loggingOfResponsesIsEnabledExplicitly_EXPECT_ResponseObserverToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.log-honeycomb-responses=true")

            .run(context -> assertThat(context).hasSingleBean(DebugResponseObserver.class));
    }

    @Configuration
    public static class SamplingHookConfig {
        @Bean
        public TraceSampler<Span> samplingHook() {
            return input -> 10;
        }
    }

    @Test
    public void GIVEN_AConfigurationWithoutASamplingHook_EXPECT_AlwaysSamplingHookToBeConfiguresInProcessor() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> {
                final SpanPostProcessor processor = context.getBean(SpanPostProcessor.class);
                assertThat(processor.runSamplerHook(null)).isEqualTo(1);
            });
    }

    @Test
    public void GIVEN_AConfigurationProvidingASamplingHook_EXPECT_SamplingHookToBeConfiguresInProcessor() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withConfiguration(UserConfigurations.of(SamplingHookConfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> {
                final Map<String, TraceSampler> beansOfType = context.getBeansOfType(TraceSampler.class);
                assertThat(beansOfType).containsKeys("samplingHook", "defaultBeelineGlobalSampler");
                final SpanPostProcessor processor = context.getBean(SpanPostProcessor.class);
                assertThat(processor.runSamplerHook(null)).isEqualTo(10);
            });
    }

    @Test
    public void GIVEN_aPlainApplicationContext_EXPECT_NoCoreBeansToBeLoaded() {
        contextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> coreBeans.forEach(beanClass -> assertThat(context).doesNotHaveBean(beanClass)));
    }

    @Test
    public void GIVEN_missingLibHoneyClass_EXPECT_NoCoreBeansToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withClassLoader(new FilteredClassLoader(LibHoney.class))

            .run(context -> coreBeans.forEach(beanClass -> assertThat(context).doesNotHaveBean(beanClass)));
    }

    @Test
    public void GIVEN_missingTracingClass_EXPECT_NoCoreBeansToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withClassLoader(new FilteredClassLoader(Tracing.class))

            .run(context -> coreBeans.forEach(beanClass -> assertThat(context).doesNotHaveBean(beanClass)));
    }

    @Configuration
    public static class UserHttpClientConfig {
        @Bean("client1")
        public RestTemplate restTemplate1(final RestTemplateBuilder builder) {
            return builder.build();
        }

        @Bean("client2")
        public RestTemplate restTemplate2(final RestTemplateBuilder builder) {
            return builder.build();
        }
    }

    @Test
    public void GIVEN_restTemplateIsDisabled_EXPECT_RestTemplateInterceptorToNotBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.rest-template.enabled=false")

            .run(context -> {
                assertThat(context).doesNotHaveBean(BeelineRestTemplateInterceptor.class);
                assertThat(context).doesNotHaveBean(RestTemplateCustomizer.class);
            });
    }

    @Test
    public void GIVEN_aNormalWebApplicationContext_EXPECT_DebugResponseObserverToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .run(context -> {
                assertThat(context).hasSingleBean(ResponseObserver.class);
                assertThat(context).hasSingleBean(DebugResponseObserver.class);
            });
    }

    @Test
    public void GIVEN_defaultResponseObserverIsDisabled_EXPECT_DebugResponseObserverToNotBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.log-honeycomb-responses=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(ResponseObserver.class);
            });
    }


    @Configuration
    public static class ResponseObserverConfig {
        @Bean
        public io.honeycomb.libhoney.ResponseObserver responseObserver() {
            return mock(ResponseObserver.class);
        }
    }

    @Test
    public void GIVEN_aUserConfigWithAResponseObserver_EXPECT_responseObserverToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withUserConfiguration(ResponseObserverConfig.class)
            .withPropertyValues(defaultProps)
            .run(context -> {
                assertThat(context).hasSingleBean(ResponseObserver.class);
                assertThat(context).doesNotHaveBean(DebugResponseObserver.class);
            });
    }


    @Test
    public void GIVEN_aNormalWebApplicationContextWithoutARestTemplate_EXPECT_RestTemplateInterceptorBeansToBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> {
                assertThat(context).hasSingleBean(BeelineRestTemplateInterceptor.class);
                assertThat(context).hasSingleBean(RestTemplateCustomizer.class);
                assertThat(context).doesNotHaveBean(RestTemplate.class);
            });
    }

    @Test
    public void GIVEN_aConfigurationWithTwoRestTemplates_EXPECT_BothToContainTheTracingInterceptor() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(BeelineAutoconfigTest.UserHttpClientConfig.class))
            .withPropertyValues(defaultProps)

            .run(context -> {
                assertThat(context).hasSingleBean(BeelineRestTemplateInterceptor.class);
                assertThat(context).hasSingleBean(RestTemplateCustomizer.class);

                final Map<String, RestTemplate> restTemplates = context.getBeansOfType(RestTemplate.class);
                restTemplates.forEach((s, restTemplate) ->
                {
                    final List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
                    assertThat(interceptors).anyMatch(interceptor -> interceptor instanceof BeelineRestTemplateInterceptor);
                });
            });
    }

    @Test
    public void GIVEN_jdbcDisabled_EXPECT_DataSourceProxyBeanPostProcessorToNotBeLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.jdbc.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(DataSourceProxyBeanPostProcessor.class)
                .doesNotHaveBean(BeelineQueryListenerForJDBC.class));
    }

    @Test
    public void GIVEN_missingPropagators_EXPECT_defaultPropagators() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .run(context -> {
                assertThat(context.getBean(BeelineProperties.class).getPropagators()).isEqualTo(Collections.singletonList("honey"));
            });
    }

    @Test
    public void GIVEN_propulatedPopagators_EXPECT_providedPropagatorList() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.propagators=honey,w3c")
            .run(context -> {
                assertThat(context.getBean(BeelineProperties.class).getPropagators()).containsExactly("honey", "w3c");
            });
    }
}
