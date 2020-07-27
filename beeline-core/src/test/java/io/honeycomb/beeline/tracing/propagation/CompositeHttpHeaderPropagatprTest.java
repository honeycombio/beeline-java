package io.honeycomb.beeline.tracing.propagation;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompositeHttpHeaderPropagatprTest {

    @Test(expected = IllegalArgumentException.class)
    public void GIVEN_nullPropagtors_EXPECT_IllegalArgumentException() {
        new CompositeHttpHeaderPropagtor(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void GIVEN_emptPropagtors_EXPECT_IllegalArgumentException() {
        new CompositeHttpHeaderPropagtor(Collections.emptyList());
    }

}
