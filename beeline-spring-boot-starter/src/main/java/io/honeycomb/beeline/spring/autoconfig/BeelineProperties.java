package io.honeycomb.beeline.spring.autoconfig;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("InstanceVariableMayNotBeInitialized")
@ConfigurationProperties(prefix = "honeycomb.beeline")
@Validated
public class BeelineProperties {
    /**
     * WriteKey is the Honeycomb authentication token.
     * Find your team write key at https://ui.honeycomb.io/account
     */
    @NotEmpty(message = "The Beeline requires a write key to be configured")
    private String writeKey;
    /**
     * Dataset is the name of the Honeycomb dataset to which to send Beeline spans and events.
     */
    @NotEmpty(message = "The Beeline requires a the dataset name to be configured")
    private String dataset;
    /**
     * APIHost is the hostname for the Honeycomb API server to which to send this event. This is usually best left
     * un-configured so the default value of {@code https://api.honeycomb.io/} is used.
     */
    @Nullable
    private URI apiHost;
    /**
     * A name to identify this service when tracing data is displayed within Honeycomb.
     * If not set, this will try to fall back to using Spring's own "spring.application.name" property.
     */
    @NotEmpty(
        message = "The Beeline requires a service name configured through this property or 'spring.application.name'")
    @Value("${spring.application.name:}")
    private String serviceName;
    /**
     * When set to false, this will disable the beeline auto configuration, so it will not instrument your application.
     */
    private boolean enabled = true;
    /**
     * The sampleRate is the rate at which to sample traces - based on the "traceId".
     * This means all Spans on a sampled trace are sent to Honeycomb.
     * The probability is {@code 1/sampleRate}. For example, if one out of every 50 traces is to be
     * submitted to Honeycomb, you would specify a sampleRate value of 50.
     * <p>
     * To not sample any traces set this to 0. <br>
     * To submit all traces set this to 1.
     */
    @PositiveOrZero(message = "The Beeline requires the sample rate to be non-negative.")
    private int sampleRate = 1;

    /**
     * Setting this will change the Beeline's Servlet Filter order. This might be useful, for example, when your
     * application makes use of a security filter and rejected requests should not be captured by the beeline.
     * <p>
     * default: Ordered#HIGHEST_PRECEDENCE
     */
    private int filterOrder = Ordered.HIGHEST_PRECEDENCE;

    /**
     * This toggles whether to log the Honeycomb server's responses to Events and Spans being sent.
     * Successes are logged at trace-level and failures at debug level.
     * The debug level is used to avoid filling logs, since the volume of Events could be relatively high.
     * <p>
     * If true an instance of io.honeycomb.beeline.spring.beans.DebugResponseObserver is wired into the application
     * context.
     * <p>
     * Alternatively, you can ignore this flag and provide your own implementation of
     * io.honeycomb.libhoney.ResponseObserver as a Spring Bean and it will used.
     * Or, you can simply add it with io.honeycomb.libhoney.HoneyClient#addResponseObserver.
     */
    private boolean logHoneycombResponses;

    @NotNull
    private BeelineProperties.RestTemplateProperties restTemplate = new RestTemplateProperties();

    /**
     * Allows the definition of a list of Ant-style path patterns that are used to match against the request path of
     * incoming HTTP requests. If a request path is matched against this list then the request will be instrumented
     * by the Beeline.
     * <p>
     * If no patterns are specified then, by default, all requests are included.
     * <p>
     * The patterns are Ant path patterns which are matched using Spring's are org.springframework.util.AntPathMatcher.
     */
    private List<String> includePathPatterns = new ArrayList<>(0);

    /**
     * Allows the definition of a list of Ant-style path patterns that are used to match against the request path of
     * incoming HTTP requests. If a request path is matched agains this list then the request will NOT be instrumented
     * by the Beeline.
     * <p>
     * If no patterns are specified then, by default, no requests are excluded.
     * <p>
     * The patterns are Ant path patterns which are matched using Spring's are org.springframework.util.AntPathMatcher.
     */
    private List<String> excludePathPatterns = new ArrayList<>(0);

    /**
     * The list of propagators to use for parsing incoming and propagate out trace information.
     * <p>
     * default: honey
     * </p>
     */
    private List<String> propagators = Collections.singletonList("honey");

    public String getDataset() {
        return dataset;
    }

    public void setDataset(final String dataset) {
        this.dataset = dataset;
    }

    public String getWriteKey() {
        return writeKey;
    }

    public void setWriteKey(final String writeKey) {
        this.writeKey = writeKey;
    }

    public URI getApiHost() {
        return apiHost;
    }

    public void setApiHost(final URI apiHost) {
        this.apiHost = apiHost;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public RestTemplateProperties getRestTemplate() {
        return restTemplate;
    }

    public void setRestTemplate(final RestTemplateProperties restTemplate) {
        this.restTemplate = restTemplate;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(final int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(final int filterOrder) {
        this.filterOrder = filterOrder;
    }

    public boolean isLogHoneycombResponses() {
        return logHoneycombResponses;
    }

    public void setLogHoneycombResponses(final boolean logHoneycombResponses) {
        this.logHoneycombResponses = logHoneycombResponses;
    }

    public List<String> getIncludePathPatterns() {
        return includePathPatterns;
    }

    public void setIncludePathPatterns(final List<String> includePathPatterns) {
        this.includePathPatterns = includePathPatterns;
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void setExcludePathPatterns(final List<String> excludePathPatterns) {
        this.excludePathPatterns = excludePathPatterns;
    }

    public List<String> getPropagators() {
        return propagators;
    }

    public void setPropagators(final List<String> propagators) {
        this.propagators = propagators;
    }

    public static class RestTemplateProperties {
        /**
         * When set to false, this will disable the configuration of beans related to RestTemplate instrumentation,
         * so deactivating the generation of spans and trace propagation through RestTemplate http client calls.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "RestTemplateProperties{" +
                   "enabled=" + enabled +
                   '}';
        }
    }

    @Override
    public String toString() {
        return "BeelineProperties{" +
               "writeKey='" + writeKey + '\'' +
               ", dataset='" + dataset + '\'' +
               ", apiHost=" + apiHost +
               ", serviceName='" + serviceName + '\'' +
               ", enabled=" + enabled +
               ", sampleRate=" + sampleRate +
               ", filterOrder=" + filterOrder +
               ", includePathPatterns=" + includePathPatterns +
               ", excludePathPatterns=" + excludePathPatterns +
               ", restTemplate=" + restTemplate +
               ", propagators=" + String.join(",", propagators) +
               '}';
    }
}
