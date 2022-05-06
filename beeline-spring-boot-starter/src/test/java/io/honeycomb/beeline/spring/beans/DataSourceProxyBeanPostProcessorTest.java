package io.honeycomb.beeline.spring.beans;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.logging.Logger;

import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
public class DataSourceProxyBeanPostProcessorTest {
    @Mock
    BeelineQueryListenerForJDBC listener;

    DataSourceProxyBeanPostProcessor processor;

    @Before
    public void setUp() {
        processor = new DataSourceProxyBeanPostProcessor(listener);
    }

    @Test
    public void GIVEN_DataSource_EXPECT_ProxiedDataSource() {
        final DataSource dataSource = mock(DataSource.class);
        final Object o = processor.postProcessAfterInitialization(dataSource, "name");
        assertThat(o).isInstanceOf(ProxyDataSource.class);
    }

    @Test
    public void GIVEN_ProxiedDataSource_EXPECT_objectIsSameObject() {
        final DataSource dataSource = mock(ProxyDataSource.class);
        final Object o = processor.postProcessAfterInitialization(dataSource, "name");
        assertThat(o).isSameAs(dataSource);
    }

    @Test
    public void GIVEN_otherObject_EXEPCT_objectIsSameObject() {
        final String sourceObj = "object";
        final Object o = processor.postProcessAfterInitialization(sourceObj, "name");
        assertThat(o).isSameAs(sourceObj);
    }

    @Test
    public void GIVEN_CustomDataSource_EXPECT_ProxiedDataSource() {
        final DataSource dataSource = mock(CustomDataSource.class);
        final Object o = processor.postProcessAfterInitialization(dataSource, "name");
        assertThat(o).isInstanceOf(ProxyDataSource.class);
    }

    @Test
    public void GIVEN_LiquibaseDataset_Expect_objectIsSame() {
        final Object dataSource = mock(LiquibaseDataSource.class);
        final Object o = processor.postProcessAfterInitialization(dataSource, "name");
        assertThat(o).isSameAs(dataSource);
    }

    private static class CustomDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public Connection getConnection(final String username, final String password) {
            return null;
        }

        @Override
        public <T> T unwrap(final Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {

        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public void setLoginTimeout(final int seconds) {

        }

        @Override
        public Logger getParentLogger() {
            return null;
        }
    }
}
