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
public class AntPathMatcherTest {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Test
    public void match() {
        // test exact matching
        assertThat(pathMatcher.match("test", "test")).isTrue();
        assertThat(pathMatcher.match("/test", "/test")).isTrue();
        // SPR-14141
        assertThat(pathMatcher.match("https://example.org", "https://example.org")).isTrue();
        assertThat(pathMatcher.match("/test.jpg", "test.jpg")).isFalse();
        assertThat(pathMatcher.match("test", "/test")).isFalse();
        assertThat(pathMatcher.match("/test", "test")).isFalse();

        // test matching with ?'s
        assertThat(pathMatcher.match("t?st", "test")).isTrue();
        assertThat(pathMatcher.match("??st", "test")).isTrue();
        assertThat(pathMatcher.match("tes?", "test")).isTrue();
        assertThat(pathMatcher.match("te??", "test")).isTrue();
        assertThat(pathMatcher.match("?es?", "test")).isTrue();
        assertThat(pathMatcher.match("tes?", "tes")).isFalse();
        assertThat(pathMatcher.match("tes?", "testt")).isFalse();
        assertThat(pathMatcher.match("tes?", "tsst")).isFalse();

        // test matching with *'s
        assertThat(pathMatcher.match("*", "test")).isTrue();
        assertThat(pathMatcher.match("test*", "test")).isTrue();
        assertThat(pathMatcher.match("test*", "testTest")).isTrue();
        assertThat(pathMatcher.match("test/*", "test/Test")).isTrue();
        assertThat(pathMatcher.match("test/*", "test/t")).isTrue();
        assertThat(pathMatcher.match("test/*", "test/")).isTrue();
        assertThat(pathMatcher.match("*test*", "AnothertestTest")).isTrue();
        assertThat(pathMatcher.match("*test", "Anothertest")).isTrue();
        assertThat(pathMatcher.match("*.*", "test.")).isTrue();
        assertThat(pathMatcher.match("*.*", "test.test")).isTrue();
        assertThat(pathMatcher.match("*.*", "test.test.test")).isTrue();
        assertThat(pathMatcher.match("test*aaa", "testblaaaa")).isTrue();
        assertThat(pathMatcher.match("test*", "tst")).isFalse();
        assertThat(pathMatcher.match("test*", "tsttest")).isFalse();
        assertThat(pathMatcher.match("test*", "test/")).isFalse();
        assertThat(pathMatcher.match("test*", "test/t")).isFalse();
        assertThat(pathMatcher.match("test/*", "test")).isFalse();
        assertThat(pathMatcher.match("*test*", "tsttst")).isFalse();
        assertThat(pathMatcher.match("*test", "tsttst")).isFalse();
        assertThat(pathMatcher.match("*.*", "tsttst")).isFalse();
        assertThat(pathMatcher.match("test*aaa", "test")).isFalse();
        assertThat(pathMatcher.match("test*aaa", "testblaaab")).isFalse();

        // test matching with ?'s and /'s
        assertThat(pathMatcher.match("/?", "/a")).isTrue();
        assertThat(pathMatcher.match("/?/a", "/a/a")).isTrue();
        assertThat(pathMatcher.match("/a/?", "/a/b")).isTrue();
        assertThat(pathMatcher.match("/??/a", "/aa/a")).isTrue();
        assertThat(pathMatcher.match("/a/??", "/a/bb")).isTrue();
        assertThat(pathMatcher.match("/?", "/a")).isTrue();

        // test matching with **'s
        assertThat(pathMatcher.match("/**", "/testing/testing")).isTrue();
        assertThat(pathMatcher.match("/*/**", "/testing/testing")).isTrue();
        assertThat(pathMatcher.match("/**/*", "/testing/testing")).isTrue();
        assertThat(pathMatcher.match("/bla/**/bla", "/bla/testing/testing/bla")).isTrue();
        assertThat(pathMatcher.match("/bla/**/bla", "/bla/testing/testing/bla/bla")).isTrue();
        assertThat(pathMatcher.match("/**/test", "/bla/bla/test")).isTrue();
        assertThat(pathMatcher.match("/bla/**/**/bla", "/bla/bla/bla/bla/bla/bla")).isTrue();
        assertThat(pathMatcher.match("/bla*bla/test", "/blaXXXbla/test")).isTrue();
        assertThat(pathMatcher.match("/*bla/test", "/XXXbla/test")).isTrue();
        assertThat(pathMatcher.match("/bla*bla/test", "/blaXXXbl/test")).isFalse();
        assertThat(pathMatcher.match("/*bla/test", "XXXblab/test")).isFalse();
        assertThat(pathMatcher.match("/*bla/test", "XXXbl/test")).isFalse();

        assertThat(pathMatcher.match("/????", "/bala/bla")).isFalse();
        assertThat(pathMatcher.match("/**/*bla", "/bla/bla/bla/bbb")).isFalse();

        assertThat(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing/")).isTrue();
        assertThat(pathMatcher.match("/*bla*/**/bla/*", "/XXXblaXXXX/testing/testing/bla/testing")).isTrue();
        assertThat(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing")).isTrue();
        assertThat(pathMatcher.match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing.jpg")).isTrue();

        assertThat(pathMatcher.match("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing/")).isTrue();
        assertThat(pathMatcher.match("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing")).isTrue();
        assertThat(pathMatcher.match("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing")).isTrue();
        assertThat(pathMatcher.match("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing/testing")).isFalse();

        assertThat(pathMatcher.match("/x/x/**/bla", "/x/x/x/")).isFalse();

        assertThat(pathMatcher.match("/foo/bar/**", "/foo/bar")).isTrue();

        assertThat(pathMatcher.match("", "")).isTrue();

        assertThat(pathMatcher.match("/{bla}.*", "/testing.html")).isTrue();
    }

    @Test
    public void matchWithNullPath() {
        assertThat(pathMatcher.match("/test", null)).isFalse();
        assertThat(pathMatcher.match("/", null)).isFalse();
        assertThat(pathMatcher.match(null, null)).isFalse();
    }

    @Test
    public void defaultCacheSetting() {
        match();
        assertThat(pathMatcher.stringMatcherCache.size() > 20).isTrue();

        for (int i = 0; i < 65536; i++) {
            pathMatcher.match("test" + i, "test");
        }
        // Cache turned off because it went beyond the threshold
        assertThat(pathMatcher.stringMatcherCache.isEmpty()).isTrue();
    }

    @Test
    public void cachePatternsSetToTrue() {
        pathMatcher.setCachePatterns(true);
        match();
        assertThat(pathMatcher.stringMatcherCache.size() > 20).isTrue();

        for (int i = 0; i < 65536; i++) {
            pathMatcher.match("test" + i, "test" + i);
        }
        // Cache keeps being alive due to the explicit cache setting
        assertThat(pathMatcher.stringMatcherCache.size() > 65536).isTrue();
    }

    @Test
    public void preventCreatingStringMatchersIfPathDoesNotStartsWithPatternPrefix() {
        pathMatcher.setCachePatterns(true);
        assertThat(pathMatcher.stringMatcherCache.size()).isEqualTo(0);

        pathMatcher.match("test?", "test");
        assertThat(pathMatcher.stringMatcherCache.size()).isEqualTo(1);

        pathMatcher.match("test?", "best");
        pathMatcher.match("test/*", "view/test.jpg");
        pathMatcher.match("test/**/test.jpg", "view/test.jpg");
        pathMatcher.match("test/{name}.jpg", "view/test.jpg");
        assertThat(pathMatcher.stringMatcherCache.size()).isEqualTo(1);
    }

    @Test
    public void creatingStringMatchersIfPatternPrefixCannotDetermineIfPathMatch() {
        pathMatcher.setCachePatterns(true);
        assertThat(pathMatcher.stringMatcherCache.size()).isEqualTo(0);

        pathMatcher.match("test", "testian");
        pathMatcher.match("test?", "testFf");
        pathMatcher.match("test/*", "test/dir/name.jpg");
        pathMatcher.match("test/{name}.jpg", "test/lorem.jpg");
        pathMatcher.match("bla/**/test.jpg", "bla/test.jpg");
        pathMatcher.match("**/{name}.jpg", "test/lorem.jpg");
        pathMatcher.match("/**/{name}.jpg", "/test/lorem.jpg");
        pathMatcher.match("/*/dir/{name}.jpg", "/*/dir/lorem.jpg");

        assertThat(pathMatcher.stringMatcherCache.size()).isEqualTo(7);
    }

    @Test
    public void cachePatternsSetToFalse() {
        pathMatcher.setCachePatterns(false);
        match();
        assertThat(pathMatcher.stringMatcherCache.isEmpty()).isTrue();
    }
}
