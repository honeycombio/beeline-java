package io.honeycomb.beeline;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.beeline.tracing.sampling.TraceSampler;
import io.honeycomb.libhoney.EventPostProcessor;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.ResponseObserver;
import io.honeycomb.libhoney.ValueSupplier;
import io.honeycomb.libhoney.builders.HoneyClientBuilder;

import javax.net.ssl.SSLContext;
import java.net.URISyntaxException;

public class BeelineBuilder {
    private Integer sampleRate = null;
    private SpanPostProcessor spanPostProcessor = null;
    private SpanBuilderFactory defaultFactory = null;
    private Tracer tracer = null;

    private final HoneyClientBuilder clientBuilder = new HoneyClientBuilder();

    /**
     * Instantiate the configured Beeline instance
     * @return the new Beeline instance
     */
    public Beeline build() {
        HoneyClient client = clientBuilder.build();
        return createBeeline(client);
    }


    @SuppressWarnings("unchecked")
    private Beeline createBeeline(HoneyClient client) {
        TraceSampler<Object> sampler = (TraceSampler<Object>) selectSampler();
        SpanPostProcessor postProcessor = spanPostProcessor != null ? spanPostProcessor : Tracing.createSpanProcessor(client, sampler);
        SpanBuilderFactory factory = defaultFactory != null ? defaultFactory : Tracing.createSpanBuilderFactory(postProcessor, sampler);
        Tracer tracer = this.tracer != null ? this.tracer : Tracing.createTracer(factory);
        return Tracing.createBeeline(tracer, factory);
    }

    private TraceSampler<?> selectSampler() {
        if (sampleRate == null) {
            return Sampling.alwaysSampler();
        }
        if (sampleRate == 0) {
            return Sampling.neverSampler();
        }
        return Sampling.deterministicSampler(sampleRate);
    }

    public BeelineBuilder addGlobalField(String name, Object field) {
        clientBuilder.addGlobalField(name, field);
        return this;
    }

    public BeelineBuilder addGlobalDynamicFields(String name, ValueSupplier<?> valueSupplier) {
        clientBuilder.addGlobalDynamicFields(name, valueSupplier);
        return this;
    }

    public BeelineBuilder addProxyCredential(String proxyHost, String username, String password) {
        clientBuilder.addProxyCredential(proxyHost, username, password);
        return this;
    }

    public BeelineBuilder dataSet(String dataSet) {
        clientBuilder.dataSet(dataSet);
        return this;
    }

    public BeelineBuilder apiHost(String apiHost) throws URISyntaxException {
        clientBuilder.apiHost(apiHost);
        return this;
    }

    public BeelineBuilder writeKey(String writeKey) {
        clientBuilder.writeKey(writeKey);
        return this;
    }

    public BeelineBuilder debug(boolean enabled) {
        clientBuilder.debug(enabled);
        return this;
    }

    public BeelineBuilder sampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        clientBuilder.sampleRate(sampleRate);
        return this;
    }

    public BeelineBuilder batchSize(int batchSize) {
        clientBuilder.batchSize(batchSize);
        return this;
    }

    public BeelineBuilder batchTimeoutMillis(long batchTimeoutMillis) {
        clientBuilder.batchTimeoutMillis(batchTimeoutMillis);
        return this;
    }

    public BeelineBuilder queueCapacity(int queueCapacity) {
        clientBuilder.queueCapacity(queueCapacity);
        return this;
    }

    public BeelineBuilder maxPendingBatchRequests(int maxPendingBatchRequests) {
        clientBuilder.maxPendingBatchRequests(maxPendingBatchRequests);
        return this;
    }

    public BeelineBuilder maxConnections(int maxConnections) {
        clientBuilder.maxConnections(maxConnections);
        return this;
    }

    public BeelineBuilder maxConnectionsPerApiHost(int maxConnectionsPerApiHost) {
        clientBuilder.maxConnectionsPerApiHost(maxConnectionsPerApiHost);
        return this;
    }

    public BeelineBuilder connectTimeout(int connectTimeout) {
        clientBuilder.connectTimeout(connectTimeout);
        return this;
    }

    public BeelineBuilder connectionRequestTimeout(int connectionRequestTimeout) {
        clientBuilder.connectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    public BeelineBuilder socketTimeout(int socketTimeout) {
        clientBuilder.socketTimeout(socketTimeout);
        return this;
    }

    public BeelineBuilder bufferSize(int bufferSize) {
        clientBuilder.bufferSize(bufferSize);
        return this;
    }

    public BeelineBuilder ioThreadCount(int ioThreadCount) {
        clientBuilder.ioThreadCount(ioThreadCount);
        return this;
    }

    public BeelineBuilder maximumHttpRequestShutdownWait(Long maximumHttpRequestShutdownWait) {
        clientBuilder.maximumHttpRequestShutdownWait(maximumHttpRequestShutdownWait);
        return this;
    }

    public BeelineBuilder additionalUserAgent(String additionalUserAgent) {
        clientBuilder.additionalUserAgent(additionalUserAgent);
        return this;
    }

    public BeelineBuilder proxyNoCredentials(String host) {
        clientBuilder.proxyNoCredentials(host);
        return this;
    }

    public BeelineBuilder sslContext(SSLContext sslContext) {
        clientBuilder.sslContext(sslContext);
        return this;
    }

    public BeelineBuilder spanPostProcessor(SpanPostProcessor spanPostProcessor) {
        this.spanPostProcessor = spanPostProcessor;
        return this;
    }

    public BeelineBuilder spanBuilderFactory(SpanBuilderFactory factory) {
        this.defaultFactory = factory;
        return this;
    }

    public BeelineBuilder tracer(Tracer tracer) {
        this.tracer = tracer;
        return this;
    }

    public BeelineBuilder maximumPendingBatchRequests(int maximumPendingBatchRequests) {
        clientBuilder.maximumPendingBatchRequests(maximumPendingBatchRequests);
        return this;
    }

    public BeelineBuilder addResponseObserver(ResponseObserver responseObserver) {
        clientBuilder.addResponseObserver(responseObserver);
        return this;
    }

    public BeelineBuilder eventPostProcessor(EventPostProcessor eventPostProcessor) {
        clientBuilder.eventPostProcessor(eventPostProcessor);
        return this;
    }

}
