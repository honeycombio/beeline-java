package io.honeycomb.beeline.spring.autoconfig;

import io.honeycomb.beeline.spring.beans.BeelineInstrumentation;
import io.honeycomb.libhoney.ValueSupplier;
import org.junit.Test;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class BeelineMetaFieldProviderTest {

    @Test
    public void GIVEN_constructorArgumetns_WHEN_creatingMetaFieldProvided_EXPECT_propertiesToHaveBeenSet() {
        final BeelineInstrumentation mock = mock(BeelineInstrumentation.class);
        final BeelineMetaFieldProvider beelineMetaFieldProvider = new BeelineMetaFieldProvider("package", "v1", "v2", "local", Collections.singletonList(mock));

        assertThat(beelineMetaFieldProvider.getLocalHostname()).isEqualTo("local");
        assertThat(beelineMetaFieldProvider.getBeelineVersion()).isEqualTo("v2");
        assertThat(beelineMetaFieldProvider.getPackageName()).isEqualTo("package");
        assertThat(beelineMetaFieldProvider.getPackageVersion()).isEqualTo("v1");
    }

    @Test
    public void GIVEN_TwoInstrumentations_WHEN_creatingMetaFieldProvided_EXPECT_instrumentationPropertiesOnlyToBeAvailableAfterContextRefreshed() {
        final BeelineInstrumentation mock1 = mock(BeelineInstrumentation.class);
        final BeelineInstrumentation mock2 = mock(BeelineInstrumentation.class);
        when(mock1.getName()).thenReturn("mockmvc");
        when(mock2.getName()).thenReturn("mockaop");

        final BeelineMetaFieldProvider beelineMetaFieldProvider = new BeelineMetaFieldProvider("package", "v1", "v2", "local", Arrays.asList(mock1, mock2));

        assertThat(beelineMetaFieldProvider.getInstrumentationCount()).isZero();
        assertThat(beelineMetaFieldProvider.getInstrumentations()).isEmpty();

        beelineMetaFieldProvider.onApplicationEvent(mock(ContextRefreshedEvent.class));

        assertThat(beelineMetaFieldProvider.getInstrumentationCount()).isEqualTo(2);
        assertThat(beelineMetaFieldProvider.getInstrumentations()).contains("mockmvc", "mockaop");
    }

    @Test
    public void GIVEN_TwoInstrumentations_WHEN_creatingMetaFieldProvided_EXPECT_dynamicFieldsropertiesOnlyToBeAvailableAfterContextRefreshed() {
        final BeelineInstrumentation mock1 = mock(BeelineInstrumentation.class);
        final BeelineInstrumentation mock2 = mock(BeelineInstrumentation.class);
        when(mock1.getName()).thenReturn("mockmvc");
        when(mock2.getName()).thenReturn("mockaop");

        final BeelineMetaFieldProvider beelineMetaFieldProvider = new BeelineMetaFieldProvider("package", "v1", "v2", "local", Arrays.asList(mock1, mock2));

        final Map<String, Object> resolved = resolveDynamicFields(beelineMetaFieldProvider);
        assertThat(resolved).contains(
            entry("meta.instrumentations", Collections.emptySet()),
            entry("meta.instrumentation_count", 0));

        beelineMetaFieldProvider.onApplicationEvent(mock(ContextRefreshedEvent.class));

        final Map<String, Object> resolved2 = resolveDynamicFields(beelineMetaFieldProvider);
        assertThat((Set<String>) resolved2.get("meta.instrumentations")).contains("mockmvc", "mockaop");
        assertThat(resolved2).contains(entry("meta.instrumentation_count", 2));
    }

    private Map<String, Object> resolveDynamicFields(final BeelineMetaFieldProvider provider) {
        final Map<String, ValueSupplier<?>> dynamicFields = provider.getDynamicFields();
        return dynamicFields.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, o -> o.getValue().supply()));
    }
}
