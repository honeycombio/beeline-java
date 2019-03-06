package io.honeycomb.beeline.spring.autoconfig;

import io.honeycomb.beeline.spring.beans.BeelineInstrumentation;
import io.honeycomb.libhoney.ValueSupplier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.honeycomb.beeline.tracing.utils.TraceFieldConstants.*;

/**
 * This class class helps setting various "meta.*" fields so that all Spans and Events sent through the Beeline's
 * HoneyClient contain them.
 *
 * <h1>Laziness</h1>
 * Laziness here is used to avoid a circular dependency issue. Namely, this provider is required by the Beelines's
 * HoneyClient. But the provider requires itself a list of all BeelineInstrumentations. BeelineInstrumentations in turn
 * require HoneyClient to be already initialised.
 * <p>
 * To break this cycle, we use Spring's @Lazy annotation, which means the instrumentation list is effectively a
 * placeholder and not usable until the application context is fully initialised - it will throw exceptions upon any
 * interaction with it.
 * <p>
 * In order to make invocations of this class safe even before the context is ready, we use the {@code isInit} flag
 * as a guard. It flips when we get the {@code ContextRefreshedEvent}.
 */
public class BeelineMetaFieldProvider implements ApplicationListener<ContextRefreshedEvent> {
    private final String packageName;
    private final String packageVersion;
    private final String beelineVersion;
    @Nullable
    private final String localHostname;
    private final List<BeelineInstrumentation> instrumentations;
    private volatile boolean isInit;

    @SuppressWarnings("BoundedWildcard")
    public BeelineMetaFieldProvider(
        final String packageName,
        final String packageVersion,
        final String beelineVersion,
        final String localHostname,
        // Annotation here is repeated as a documentation hint
        @Lazy final List<BeelineInstrumentation> instrumentations
    ) {
        this.packageName = packageName;
        this.packageVersion = packageVersion;
        this.beelineVersion = beelineVersion;
        this.localHostname = localHostname;
        this.instrumentations = instrumentations;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public String getBeelineVersion() {
        return beelineVersion;
    }

    @Nullable
    public String getLocalHostname() {
        return localHostname;
    }

    public Set<String> getInstrumentations() {
        if (isInit) {
            final Set<String> set = new HashSet<>(instrumentations.size());
            for (final BeelineInstrumentation instrumentation : instrumentations) {
                final String name = instrumentation.getName();
                set.add(name);
            }
            return set;
        } else {
            return Collections.emptySet();
        }
    }

    public int getInstrumentationCount() {
        return isInit ? instrumentations.size() : 0;
    }

    public Map<String, ValueSupplier<?>> getDynamicFields() {
        final Map<String, ValueSupplier<?>> fields = new HashMap<>(6);
        fields.put(INSTRUMENTATIONS_FIELD, this::getInstrumentations);
        fields.put(INSTRUMENTATIONS_COUNT_FIELD, this::getInstrumentationCount);
        return fields;
    }

    public Map<String, ?> getStaticFields() {
        final Map<String, String> fields = new HashMap<>(6);
        if (getLocalHostname() != null) {
            fields.put(LOCAL_HOSTNAME_FIELD, getLocalHostname());
        }
        fields.put(PACKAGE_FIELD, getPackageName());
        fields.put(PACKAGE_VERSION_FIELD, getPackageVersion());
        fields.put(BEELINE_VERSION_FIELD, getBeelineVersion());
        return fields;
    }

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        isInit = true;
    }
}
