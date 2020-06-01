package io.honeycomb.beeline.spring.sleuth.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "honeycomb.beeline.sleuth")
public class BeelineSleuthProperties {

    /**
     * When set to false, this will disable Sleuth auto-configuration from sending data to Honeycomb.
     */
    private boolean enabled = true;
    /**
     * When set to false, this will prevent creation of a sleuth reporter to send tracing data to Honeycomb. Set to false if the application has a customized Reporter (to avoid duplicate events sent to Honeycomb).
     */
    private boolean enableReporter = true;

    public boolean isEnabled() {
        return enabled;
    }

    public BeelineSleuthProperties setEnabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isEnableReporter() {
        return enableReporter;
    }

    public BeelineSleuthProperties setEnableReporter(final boolean enableReporter) {
        this.enableReporter = enableReporter;
        return this;
    }
}
