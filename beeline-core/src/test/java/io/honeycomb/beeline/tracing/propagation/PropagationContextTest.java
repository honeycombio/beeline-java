package io.honeycomb.beeline.tracing.propagation;

import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class PropagationContextTest {

    @Test
    public void GIVEN_aContextInitialisedWithArguments_EXPECT_toContainArguments() {
        final PropagationContext context = new PropagationContext("123-456", "Abc-Def", "myDataset", Collections.singletonMap("key", "value"));

        assertThat(context.getTraceFields()).containsExactly(entry("key", "value"));
        assertThat(context.getSpanId()).isEqualTo("Abc-Def");
        assertThat(context.getTraceId()).isEqualTo("123-456");
        assertThat(context.getDataset()).isEqualTo("myDataset");
    }

    @Test
    public void GIVEN_aContextWithoutIdsButWithTraceFields_EXPECT_ToBeValid() {
        final PropagationContext context = new PropagationContext(null, null, null, Collections.singletonMap("key", "value"));

        assertThat(context.getTraceFields()).containsExactly(entry("key", "value"));
        assertThat(context.getSpanId()).isNull();
        assertThat(context.getTraceId()).isNull();
        assertThat(context.getDataset()).isNull();
    }

    @Test
    public void GIVEN_spanIdHasValueButTraceIdIsNull_WHEN_constructingContext_EXPECT_IAEToBeThrow() {
        assertThatThrownBy(() -> new PropagationContext(null, "spanid", null, Collections.singletonMap("key", "value")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void GIVEN_anEmptyContext_EXPECT_nullValues() {
        final PropagationContext emptyContext = PropagationContext.emptyContext();

        assertThat(emptyContext.getTraceFields()).isEmpty();
        assertThat(emptyContext.getSpanId()).isNull();
        assertThat(emptyContext.getTraceId()).isNull();
        assertThat(emptyContext.getDataset()).isNull();
    }

    @Test
    public void GIVEN_aNullInitialisedEmptyConetxt_EXPECT_toEqualEmptyContextInstance() {
        assertThat(PropagationContext.emptyContext())
            .isEqualTo(new PropagationContext(null, null, null, Collections.emptyMap()))
            .isSameAs(PropagationContext.emptyContext());
    }

    @Test
    public void checkEmptyContextToString() {
        assertThat(PropagationContext.emptyContext().toString()).isEqualTo(new PropagationContext(null, null, null, Collections.emptyMap()).toString());
    }

    @Test
    public void checkEmptyContextHashCode() {
        assertThat(PropagationContext.emptyContext().hashCode()).isEqualTo(new PropagationContext(null, null, null, Collections.emptyMap()).hashCode());
    }
}
