package io.honeycomb.beeline.builder;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.libhoney.EventPostProcessor;
import io.honeycomb.libhoney.ResponseObserver;
import io.honeycomb.libhoney.ValueSupplier;
import io.honeycomb.libhoney.builders.HoneyClientBuilder;
import io.honeycomb.libhoney.transport.Transport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.net.ssl.SSLContext;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BeelineBuilderTest {

    private static final String classicWriteKey = "e38be416d0d68f9ed1e96432ac1a3380";
    private static final String nonClassicWriteKey = "d68f9ed1e96432ac1a3380";

    BeelineBuilder builder;
    HoneyClientBuilder mockBuilder;
    @Before
    public void setUp() {
        builder = new BeelineBuilder();
        builder.clientBuilder = mockBuilder = spy(builder.clientBuilder);
        when(mockBuilder.build()).thenCallRealMethod();
    }

    @Test
    public void addGlobalField() {
        final Beeline beeline = builder.addGlobalField("name", "value").build();
        verify(mockBuilder, times(1)).addGlobalField("name", "value");
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void addGlobalDynamicFields() {
        final Beeline beeline = builder.addGlobalDynamicFields("name", mock(ValueSupplier.class)).build();
        verify(mockBuilder, times(1)).addGlobalDynamicFields(eq("name"), any(ValueSupplier.class));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void addProxyCredential() {
        final Beeline beeline = builder.addProxy("proxy.domain.com:8443", "user", "secret").build();
        verify(mockBuilder, times(1)).addProxy("proxy.domain.com:8443", "user", "secret");
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void dataSet() {
        final Beeline beeline = builder.dataSet("set").build();
        verify(mockBuilder, times(1)).dataSet("set");
        completeNegativeVerification();
    }

    @Test
    public void apiHost() throws URISyntaxException {
        final Beeline beeline = builder.apiHost("host:80").build();
        verify(mockBuilder, times(1)).apiHost("host:80");
        verify(mockBuilder, times(1)).apiHost(any(URI.class));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void writeKey() {
        final Beeline beeline = builder.writeKey(classicWriteKey).build();
        verify(mockBuilder, times(1)).writeKey(classicWriteKey);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void testDebugEnabled() {
        final Beeline beeline = builder.debug(true).build();
        verify(mockBuilder, times(1)).debug(true);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }
    @Test
    public void testDebugDisabled() {
        final Beeline beeline = builder.debug(false).build();
        verify(mockBuilder, times(1)).debug(false);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void sampleRate() {
        final Beeline beeline = builder.sampleRate(123).build();
        verify(mockBuilder, times(1)).sampleRate(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void batchSize() {
        final Beeline beeline = builder.batchSize(123).build();
        verify(mockBuilder, times(1)).batchSize(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void batchTimeoutMillis() {
        final Beeline beeline = builder.batchTimeoutMillis(123).build();
        verify(mockBuilder, times(1)).batchTimeoutMillis(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void queueCapacity() {
        builder.queueCapacity(123).build();
        verify(mockBuilder, times(1)).queueCapacity(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void maxPendingBatchRequests() {
        builder.maxPendingBatchRequests(123).build();
        verify(mockBuilder, times(1)).maxPendingBatchRequests(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void maxConnections() {
        builder.maxConnections(123).build();
        verify(mockBuilder, times(1)).maxConnections(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void maxConnectionsPerApiHost() {
        builder.maxConnectionsPerApiHost(123).build();
        verify(mockBuilder, times(1)).maxConnectionsPerApiHost(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void connectionTimeout() {
        final Beeline beeline = builder.connectionTimeout(123).build();
        verify(mockBuilder, times(1)).connectionTimeout(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void connectionRequestTimeout() {
        builder.connectionRequestTimeout(123).build();
        verify(mockBuilder, times(1)).connectionRequestTimeout(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void socketTimeout() {
        builder.socketTimeout(123).build();
        verify(mockBuilder, times(1)).socketTimeout(123);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void bufferSize() {
        builder.bufferSize(5_000).build();
        verify(mockBuilder, times(1)).bufferSize(5_000);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void ioThreadCount() {
        builder.ioThreadCount(1).build();
        verify(mockBuilder, times(1)).ioThreadCount(1);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void maximumHttpRequestShutdownWait() {
        builder.maximumHttpRequestShutdownWait(345L).build();
        verify(mockBuilder, times(1)).maximumHttpRequestShutdownWait(345L);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void additionalUserAgent() {
        builder.additionalUserAgent("agent").build();
        verify(mockBuilder, times(1)).additionalUserAgent("agent");
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void addProxyNoCredentials() {
        builder.addProxy("proxyHost").build();
        verify(mockBuilder, times(1)).addProxy(anyString());
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void sslContext() {
        final SSLContext mockContext = mock(SSLContext.class);
        builder.sslContext(mockContext).build();
        verify(mockBuilder, times(1)).sslContext(any(SSLContext.class));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void addResponseObserver() {
        builder.addResponseObserver(mock(ResponseObserver.class)).build();
        verify(mockBuilder, times(1)).addResponseObserver(any(ResponseObserver.class));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void eventPostProcessor() {
        builder.eventPostProcessor(mock(EventPostProcessor.class)).build();
        verify(mockBuilder, times(1)).eventPostProcessor(any(EventPostProcessor.class));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void transport() {
        final Transport mockTransport = mock(Transport.class);
        final Beeline beeline = builder.transport(mockTransport).build();

        verify(mockBuilder, times(1)).transport(mockTransport);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        verifyNoMoreInteractions(mockTransport);
        completeNegativeVerification();
    }

    @Test
    public void noWriteKeyGetsDefaultClassicDataset() {
        final Beeline beeline = builder.build();

        assertThat(beeline.getServiceName()).isEqualTo(Beeline.resolveServiceName(""));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void emptyWriteKeyGetsDefaultClassicDataset() {
        final Beeline beeline = builder.writeKey("").build();

        assertThat(beeline.getServiceName()).isEqualTo(Beeline.resolveServiceName(""));
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void classicWriteKeyGetsDefaultClassicDataset() {
        final Beeline beeline = builder.writeKey(classicWriteKey).build();

        assertThat(beeline.getServiceName()).isEqualTo(Beeline.resolveServiceName(""));
        verify(mockBuilder, times(1)).writeKey(classicWriteKey);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    @Test
    public void modernWriteKeyGetsDefaultNonClassicDataset() {
        final Beeline beeline = builder.writeKey(nonClassicWriteKey).build();

        assertThat(beeline.getServiceName()).isEqualTo(Beeline.resolveServiceName(""));
        verify(mockBuilder, times(1)).writeKey(nonClassicWriteKey);
        verify(mockBuilder, times(1)).dataSet("unknown_service");
        completeNegativeVerification();
    }

    @Test
    public void writeKeyDatasetAndServiceNameAreTrimmedOfWhiteSpace() {
        final Beeline beeline = builder
            .writeKey(" " + classicWriteKey + " ")
            .dataSet(" my-dataset ")
            .serviceName(" my-service ")
            .build();

        assertThat(beeline.getServiceName()).isEqualTo("my-service");
        verify(mockBuilder, times(1)).writeKey(classicWriteKey);
        verify(mockBuilder, times(1)).dataSet("my-dataset");
        completeNegativeVerification();
    }

    @Test
    public void emptyServiceNameGetsDefaultServiceName() {
        final Beeline beeline = builder
            .writeKey(classicWriteKey)
            .serviceName("")
            .build();

        assertThat(beeline.getServiceName()).startsWith(Beeline.resolveServiceName(""));
        verify(mockBuilder, times(1)).writeKey(classicWriteKey);
        verify(mockBuilder, times(1)).dataSet("beeline-java");
        completeNegativeVerification();
    }

    private void completeNegativeVerification(){
        verify(mockBuilder, times(1)).build();
        verifyNoMoreInteractions(mockBuilder);
    }
}
