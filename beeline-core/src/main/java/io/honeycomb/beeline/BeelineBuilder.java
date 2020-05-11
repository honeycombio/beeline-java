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
    private HoneyClient client = null;
    private SpanPostProcessor spanPostProcessor = null;
    private SpanBuilderFactory defaultFactory = null;
    private Tracer tracer = null;

    private HoneyClientBuilder clientBuilder = new HoneyClientBuilder();

    public Beeline build() throws URISyntaxException {
        client = createClient();
        return createBeeline();
    }


    @SuppressWarnings("unchecked")
    private Beeline createBeeline() {
        TraceSampler<Object> sampler = (TraceSampler<Object>) createSampler();
        SpanPostProcessor postProcessor = spanPostProcessor != null ? spanPostProcessor : Tracing.createSpanProcessor(client, sampler);
        SpanBuilderFactory factory = defaultFactory != null ? defaultFactory : Tracing.createSpanBuilderFactory(postProcessor, sampler);
        Tracer tracer = this.tracer != null ? this.tracer : Tracing.createTracer(factory);
        return Tracing.createBeeline(tracer, factory);
    }

    private TraceSampler<?> createSampler() {
        if (sampleRate == null) {
            return Sampling.alwaysSampler();
        }
        if (sampleRate == 0) {
            return Sampling.neverSampler();
        }
        return Sampling.deterministicSampler(sampleRate);
    }

    private HoneyClient createClient() throws URISyntaxException {
        return clientBuilder.build();
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

    public BeelineBuilder setDataSet(String dataSet) {
        clientBuilder.setDataSet(dataSet);
        return this;
    }

    public BeelineBuilder setApiHost(String apiHost) throws URISyntaxException {
        clientBuilder.setApiHost(apiHost);
        return this;
    }

    public BeelineBuilder setWriteKey(String writeKey) {
        clientBuilder.setWriteKey(writeKey);
        return this;
    }

    public BeelineBuilder setDebugFlag(boolean debugFlag) {
        clientBuilder.setDebugFlag(debugFlag);
        return this;
    }

    public BeelineBuilder setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    public BeelineBuilder setBatchSize(int batchSize) {
        clientBuilder.setBatchSize(batchSize);
        return this;
    }

    public BeelineBuilder setBatchTimeoutMillis(long batchTimeoutMillis) {
        clientBuilder.setBatchTimeoutMillis(batchTimeoutMillis);
        return this;
    }

    public BeelineBuilder setQueueCapacity(int queueCapacity) {
        clientBuilder.setQueueCapacity(queueCapacity);
        return this;
    }

    public BeelineBuilder setMaxPendingBatchRequests(int maxPendingBatchRequests) {
        clientBuilder.setMaxPendingBatchRequests(maxPendingBatchRequests);
        return this;
    }

    public BeelineBuilder setMaxConnections(int maxConnections) {
        clientBuilder.setMaxConnections(maxConnections);
        return this;
    }

    public BeelineBuilder setMaxConnectionsPerApiHost(int maxConnectionsPerApiHost) {
        clientBuilder.setMaxConnectionsPerApiHost(maxConnectionsPerApiHost);
        return this;
    }

    public BeelineBuilder setConnectTimeout(int connectTimeout) {
        clientBuilder.setConnectTimeout(connectTimeout);
        return this;
    }

    public BeelineBuilder setConnectionRequestTimeout(int connectionRequestTimeout) {
        clientBuilder.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    public BeelineBuilder setSocketTimeout(int socketTimeout) {
        clientBuilder.setSocketTimeout(socketTimeout);
        return this;
    }

    public BeelineBuilder setBufferSize(int bufferSize) {
        clientBuilder.setBufferSize(bufferSize);
        return this;
    }

    public BeelineBuilder setIoThreadCount(int ioThreadCount) {
        clientBuilder.setIoThreadCount(ioThreadCount);
        return this;
    }

    public BeelineBuilder setMaximumHttpRequestShutdownWait(Long maximumHttpRequestShutdownWait) {
        clientBuilder.setMaximumHttpRequestShutdownWait(maximumHttpRequestShutdownWait);
        return this;
    }

    public BeelineBuilder setAdditionalUserAgent(String additionalUserAgent) {
        clientBuilder.setAdditionalUserAgent(additionalUserAgent);
        return this;
    }

    public BeelineBuilder setProxyHostNoCredentials(String proxyHost) {
        clientBuilder.setProxyNoCredentials(proxyHost);
        return this;
    }

    public BeelineBuilder setSslContext(SSLContext sslContext) {
        clientBuilder.setSSLContext(sslContext);
        return this;
    }

    public BeelineBuilder setSpanPostProcessor(SpanPostProcessor spanPostProcessor) {
        this.spanPostProcessor = spanPostProcessor;
        return this;
    }

    public BeelineBuilder setFactory(SpanBuilderFactory factory) {
        this.defaultFactory = factory;
        return this;
    }

    public BeelineBuilder setTracer(Tracer tracer) {
        this.tracer = tracer;
        return this;
    }

    public BeelineBuilder setMaximumPendingBatchRequests(int maximumPendingBatchRequests) {
        clientBuilder.setMaximumPendingBatchRequests(maximumPendingBatchRequests);
        return this;
    }

    public BeelineBuilder addResponseObserver(ResponseObserver responseObserver) {
        clientBuilder.addResponseObserver(responseObserver);
        return this;
    }

    public BeelineBuilder setEventPostProcessor(EventPostProcessor eventPostProcessor) {
        clientBuilder.setEventPostProcessor(eventPostProcessor);
        return this;
    }

}
