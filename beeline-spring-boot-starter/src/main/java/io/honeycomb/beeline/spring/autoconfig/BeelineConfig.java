package io.honeycomb.beeline.spring.autoconfig;

import io.honeycomb.beeline.spring.beans.BeelineHandlerInterceptor;
import io.honeycomb.beeline.spring.beans.BeelineInstrumentation;
import io.honeycomb.beeline.spring.beans.BeelineQueryListenerForJDBC;
import io.honeycomb.beeline.spring.beans.BeelineRestTemplateInterceptor;
import io.honeycomb.beeline.spring.beans.DataSourceProxyBeanPostProcessor;
import io.honeycomb.beeline.spring.beans.DebugResponseObserver;
import io.honeycomb.beeline.spring.beans.SpringServletFilter;
import io.honeycomb.beeline.spring.beans.aspects.SpanAspect;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.propagation.HttpHeaderPropagationCodecFactory;
import io.honeycomb.beeline.tracing.propagation.PropagationCodec;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.EventPostProcessor;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import io.honeycomb.libhoney.ResponseObserver;
import io.honeycomb.libhoney.TransportOptions;
import io.honeycomb.libhoney.builders.HoneyClientBuilder;
import io.honeycomb.libhoney.transport.Transport;
import io.honeycomb.libhoney.transport.impl.BatchingHttpTransport;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

public class BeelineConfig {
    private static final String BEELINE_USER_AGENT_PREFIX = "beeline/";

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private BeelineProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public Beeline defaultBeeline(final Tracer tracer, final SpanBuilderFactory factory, final BeelineProperties beelineProperties) {
        return Tracing.createBeeline(tracer, factory, beelineProperties.getServiceName());
    }

    @Bean
    @ConditionalOnMissingBean
    public Tracer defaultBeelineTracer(final SpanBuilderFactory factory) {
        return Tracing.createTracer(factory);
    }

    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "BoundedWildcard"})
    @Bean
    @ConditionalOnMissingBean
    public HoneyClient defaultBeelineHoneyClient(final BeelineProperties beelineProperties,
                                                 final BeelineMetaFieldProvider metaFieldProvider,
                                                 final Transport transport,
                                                 final Optional<ResponseObserver> maybeObserver,
                                                 final Optional<EventPostProcessor> maybePostProcessor) {
        // Spring will handle shutdown of the client, see javadoc of the Bean#destroyMethod annotation parameter

        final HoneyClientBuilder builder = new HoneyClientBuilder()
            // todo use dataset if classic
            .dataSet(beelineProperties.getServiceName())
            .writeKey(beelineProperties.getWriteKey())
            .transport(transport);

        // set apiHost if not empty
        if (beelineProperties.getApiHost() != null) {
            try {
                builder.apiHost(beelineProperties.getApiHost());
            } catch (URISyntaxException e) {
                // eat error for now
            }
        }

        // map static and dynamic fields
        metaFieldProvider.getStaticFields().forEach(builder::addGlobalField);
        metaFieldProvider.getDynamicFields().forEach(builder::addGlobalDynamicFields);

        // if we have a proxy hostname
        if (!beelineProperties.getProxyHostname().isEmpty()) {
            // if either username or password are empty
            if (beelineProperties.getProxyUsername().isEmpty() || beelineProperties.getProxyPassword().isEmpty()) {
                // add proxy without username & password
                builder.addProxy(beelineProperties.getProxyHostname());
            } else {
                // add proxy with username & password
                builder.addProxy(
                    beelineProperties.getProxyHostname(),
                    beelineProperties.getProxyUsername(),
                    beelineProperties.getProxyPassword());
            }
        }

        maybePostProcessor.ifPresent(builder::eventPostProcessor);

        // final HoneyClient honeyClient = new HoneyClient(options, transport);
        final HoneyClient honeyClient = builder.build();
        maybeObserver.ifPresent(honeyClient::addResponseObserver);

        return honeyClient;
    }

    @Bean
    @ConditionalOnProperty(name = "honeycomb.beeline.log-honeycomb-responses", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ResponseObserver defaultBeelineResponseObserver() {
        return new DebugResponseObserver();
    }

    @Bean(destroyMethod = "") // let HoneyClient perform the close on shutdown
    @ConditionalOnMissingBean
    public Transport defaultBeelineTransport() {
        final String additionalUserAgent = BEELINE_USER_AGENT_PREFIX + BeelineConfigUtils.getBeelineVersion();
        final TransportOptions transportOptions = LibHoney
            .transportOptions()
            .setAdditionalUserAgent(additionalUserAgent)
            .build();
        return BatchingHttpTransport.init(transportOptions);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringServletFilter defaultBeelineFilter(final BeelineProperties beelineProperties,
                                                    final Beeline beeline) {
        return new SpringServletFilter(
            beelineProperties.getServiceName(),
            beelineProperties.getIncludePathPatterns(),
            beelineProperties.getExcludePathPatterns(),
            beeline,
            HttpHeaderPropagationCodecFactory.create(properties.getPropagators())
        );
    }

    @Bean
    public TraceSampler<String> defaultBeelineGlobalSampler(final BeelineProperties beelineProps) {
        return Sampling.deterministicSampler(beelineProps.getSampleRate());
    }

    @SuppressWarnings("BoundedWildcard")
    @Bean
    @ConditionalOnMissingBean
    public SpanBuilderFactory defaultBeelineSpanBuilderFactory(final SpanPostProcessor spanPostProcessor,
                                                               final TraceSampler<String> globalSampler) {
        return Tracing.createSpanBuilderFactory(spanPostProcessor, globalSampler);
    }

    @SuppressWarnings({"BoundedWildcard", "OptionalUsedAsFieldOrParameterType"})
    @Bean
    @ConditionalOnMissingBean
    public SpanPostProcessor defaultBeelineSpanProcessor(final HoneyClient client,
                                                         final Optional<TraceSampler<Span>> maybeSamplingHook) {
        final TraceSampler<? super Span> samplingHook = maybeSamplingHook.isPresent() ?
            maybeSamplingHook.get() :
            Sampling.alwaysSampler();
        return Tracing.createSpanProcessor(client, samplingHook);
    }

    @Bean
    @ConditionalOnMissingBean
    public BeelineMetaFieldProvider beelineProps(@Lazy final List<BeelineInstrumentation> instrumentations) {
        return new BeelineMetaFieldProvider(
            BeelineConfigUtils.getSpringName(),
            BeelineConfigUtils.getSpringVersion(),
            BeelineConfigUtils.getBeelineVersion(),
            BeelineConfigUtils.tryGetLocalHostname(),
            instrumentations);
    }

    @Bean
    @ConditionalOnMissingBean
    public BeelineHandlerInterceptor defaultBeelineInterceptor(final Tracer tracer, final SpanBuilderFactory factory) {
        return new BeelineHandlerInterceptor(tracer, factory);
    }

    @Bean
    public FilterRegistrationBean<SpringServletFilter> beelineFilterRegistration(
        final SpringServletFilter filter,
        final BeelineProperties properties
    ) {
        // SpringServletContainerInitializer
        final FilterRegistrationBean<SpringServletFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(
            DispatcherType.REQUEST,
            DispatcherType.ASYNC,
            DispatcherType.ERROR,
            DispatcherType.FORWARD,
            DispatcherType.INCLUDE);
        registration.setOrder(properties.getFilterOrder());
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "honeycomb.beeline.rest-template.enabled", matchIfMissing = true)
    public BeelineRestTemplateInterceptor defaultBeelineRestTemplateInterceptor(final Tracer tracer) {
        PropagationCodec<Map<String, String>> propagationCodec = HttpHeaderPropagationCodecFactory.create(properties.getPropagators());
        return new BeelineRestTemplateInterceptor(tracer, propagationCodec);
    }

    @Bean
    @ConditionalOnProperty(name = "honeycomb.beeline.rest-template.enabled", matchIfMissing = true)
    public RestTemplateCustomizer defaultBeelineRestTemplateCustomizer(
        final BeelineRestTemplateInterceptor interceptor
    ) {
        return interceptor.customizer();
    }

    @Bean
    public SpanAspect defaultBeelineSpanAspect(final Tracer tracer) {
        return new SpanAspect(tracer);
    }

    @Bean
    @ConditionalOnProperty(name = "honeycomb.beeline.jdbc.enabled", havingValue = "true", matchIfMissing = true)
    public BeelineQueryListenerForJDBC beelineQueryListenerForJDBC(Beeline beeline){
        return new BeelineQueryListenerForJDBC(beeline);
    }

    @Bean
    @ConditionalOnProperty(name = "honeycomb.beeline.jdbc.enabled", havingValue = "true", matchIfMissing = true)
    public DataSourceProxyBeanPostProcessor proxyBeanPostProcessor(BeelineQueryListenerForJDBC listener){
        return new DataSourceProxyBeanPostProcessor(listener);
    }
}
