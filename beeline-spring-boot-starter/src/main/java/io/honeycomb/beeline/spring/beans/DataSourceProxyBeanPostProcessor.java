package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.tracing.Beeline;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

    private final Beeline beeline;

    public DataSourceProxyBeanPostProcessor(final Beeline beeline) {
        this.beeline = beeline;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (!DataSource.class.isAssignableFrom(bean.getClass()) || ProxyDataSource.class.isAssignableFrom(bean.getClass())) {
            return bean;
        }

        return ProxyDataSourceBuilder.create((DataSource) bean)
            .name(beanName)
            .listener(new BeelineQueryListenerForJDBC(beeline))
            .build();
    }

}
