package io.honeycomb.beeline.tracing.utils;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p><b>NB:</b>: This class is forked from the <a href="http://www.springframework.org">Spring Framework</a> with modifications.
 * <p>
 * This was to avoid having a dependency on Spring in the core Beeline module, but also to avoid rewriting an
 * implementation of Ant-style path pattern matching.
 *
 * <p>As per the Apache 2.0 license, the original copyright notice and all author and copyright information have
 * remained in tact.</p>
 */
public class StringUtilsTest {
    @Test
    public void hasTextBlank() {
        String blank = "          ";
        assertThat(StringUtils.hasText(blank)).isEqualTo(false);
    }

    @Test
    public void hasTextNullEmpty() {
        assertThat(StringUtils.hasText(null)).isEqualTo(false);
        assertThat(StringUtils.hasText("")).isEqualTo(false);
    }

    @Test
    public void hasTextValid() {
        assertThat(StringUtils.hasText("t")).isEqualTo(true);
    }

    @Test
    public void tokenizeToStringArray() {
        String[] sa = StringUtils.tokenizeToStringArray("a,b , ,c", ",", true, true);
        assertThat(sa.length).isEqualTo(3);
        assertThat(sa[0].equals("a") && sa[1].equals("b") && sa[2].equals("c")).as("components are correct").isTrue();
    }

    @Test
    public void tokenizeToStringArrayWithNotIgnoreEmptyTokens() {
        String[] sa = StringUtils.tokenizeToStringArray("a,b , ,c", ",", true, false);
        assertThat(sa.length).isEqualTo(4);
        assertThat(sa[0].equals("a") && sa[1].equals("b") && sa[2].equals("") && sa[3].equals("c")).as("components are correct").isTrue();
    }

    @Test
    public void tokenizeToStringArrayWithNotTrimTokens() {
        String[] sa = StringUtils.tokenizeToStringArray("a,b ,c", ",", false, true);
        assertThat(sa.length).isEqualTo(3);
        assertThat(sa[0].equals("a") && sa[1].equals("b ") && sa[2].equals("c")).as("components are correct").isTrue();
    }
}
