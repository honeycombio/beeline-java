package io.honeycomb.beeline.spring.autoconfig;

import io.honeycomb.beeline.tracing.Beeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.lang.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;

final class BeelineConfigUtils {
    private static final Logger LOG = LoggerFactory.getLogger(BeelineConfigUtils.class);

    @Nullable
    private static final String LOCAL_HOSTNAME;
    private static final String SPRING_VERSION = SpringBootVersion.getVersion();
    private static final String IMPLEMENTATION_TITLE = SpringBootVersion.class.getPackage().getImplementationTitle();
    private static final String IMPLEMENTATION_VERSION
        = Beeline.class.getPackage().getImplementationVersion(); // null when running in the ide

    static {
        InetAddress localHost = null;
        try {
            localHost = InetAddress.getLocalHost();
        } catch (final UnknownHostException e) {
            LOG.info("Could not determine local host", e);
        }
        LOCAL_HOSTNAME = localHost == null ? null : localHost.getHostName();
    }

    private BeelineConfigUtils() {
        //utils
    }

    @Nullable
    static String tryGetLocalHostname() {
        return LOCAL_HOSTNAME;
    }

    static String getSpringVersion() {
        return SPRING_VERSION;
    }

    static String getSpringName() {
        return IMPLEMENTATION_TITLE;
    }

    static String getBeelineVersion() {
        return IMPLEMENTATION_VERSION;
    }
}
