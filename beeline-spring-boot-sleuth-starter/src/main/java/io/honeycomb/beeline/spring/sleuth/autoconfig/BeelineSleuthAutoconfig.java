package io.honeycomb.beeline.spring.sleuth.autoconfig;

import io.honeycomb.beeline.spring.autoconfig.BeelineProperties;
import io.honeycomb.beeline.spring.beans.BraveBeelineReporter;
import io.honeycomb.beeline.tracing.Beeline;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.Reporter;

@Configuration
@EnableConfigurationProperties({BeelineProperties.class, BeelineSleuthProperties.class})
@ConditionalOnProperty(name = "honeycomb.beeline.sleuth.enabled", matchIfMissing = true, havingValue = "true")
@AutoConfigureBefore(TraceAutoConfiguration.class)
public class BeelineSleuthAutoconfig {

    @Bean
    @ConditionalOnProperty(name = "honeycomb.beeline.sleuth.reporter-enabled", matchIfMissing = true, havingValue = "true")
    public Reporter<?> beelineReporter(BeelineProperties properties, Beeline beeline) {
        return new BraveBeelineReporter(beeline, properties);
    }
}
