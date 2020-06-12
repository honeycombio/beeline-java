package io.honeycomb.beeline.spring.sleuth.autoconfig;

import io.honeycomb.beeline.spring.autoconfig.BeelineAutoconfig;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class BeelineSleuthAutoconfigTest {
    final static private String[] defaultProps = {
        "honeycomb.beeline.write-key=someKey",
        "honeycomb.beeline.dataset=someData",
        "honeycomb.beeline.enabled=true",
        "honeycomb.beeline.service-name=someService",
        "honeycomb.beeline.sleuth.enabled=true",
        "honeycomb.beeline.sleuth.reporter-enabled=true"
    };
    final private ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    final private WebApplicationContextRunner webApplicationContextRunner = new WebApplicationContextRunner();

    @Test
    public void GIVEN_aWebApplicationContext_EXPECT_AllCoreBeansToLoaded() {
        webApplicationContextRunner
            .withConfiguration(AutoConfigurations.of(BeelineSleuthAutoconfig.class, BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .run(context -> assertThat(context).hasSingleBean(Reporter.class));
    }

    @Test
    public void GIVEN_sleuthDisabledViaProperty_EXPECT_NoReporter() {
        webApplicationContextRunner.withConfiguration(AutoConfigurations.of(BeelineSleuthAutoconfig.class, BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.sleuth.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(Reporter.class));
    }

    @Test
    public void GIVEN_sleuthReporterDisabledViaProperty_EXPECT_NoReporter() {
        webApplicationContextRunner.withConfiguration(AutoConfigurations.of(BeelineSleuthAutoconfig.class, BeelineAutoconfig.class))
            .withPropertyValues(defaultProps)
            .withPropertyValues("honeycomb.beeline.sleuth.reporter-enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(Reporter.class));
    }
}
