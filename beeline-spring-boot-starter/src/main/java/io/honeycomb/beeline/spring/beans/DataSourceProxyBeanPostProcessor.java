package io.honeycomb.beeline.spring.beans;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

public class DataSourceProxyBeanPostProcessor implements BeanPostProcessor {

    private final BeelineQueryListenerForJDBC listener;

    public DataSourceProxyBeanPostProcessor(BeelineQueryListenerForJDBC listener) {
        this.listener = listener;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (!DataSource.class.isAssignableFrom(bean.getClass()) || ProxyDataSource.class.isAssignableFrom(bean.getClass())) {
            return bean;
        }

        return ProxyDataSourceBuilder.create((DataSource) bean)
            .name(beanName)
            .listener(listener)
            .build();
    }

}
