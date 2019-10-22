package io.honeycomb.beeline.tracing.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Utilities for String parsing and manipulation; primarily for use with {@link AntPathMatcher} and {@link io.honeycomb.beeline.tracing.propagation.BeelineServletFilter}.
 *
 * <p><b>NB:</b>: This class is forked from the <a href="http://www.springframework.org">Spring Framework</a> with modifications.
 * <p>
 * This was to avoid having a dependency on Spring in the core Beeline module, but also to avoid rewriting an
 * implementation of Ant-style path pattern matching.
 *
 * <p>As per the Apache 2.0 license, the original copyright notice and all author and copyright information have
 * remained in tact.</p>
 */
public class StringUtils {
    private static final String[] EMPTY_STRING_ARRAY = {};

    /**
     * Check whether the given {@code CharSequence} contains actual <em>text</em>.
     * <p>More specifically, this method returns {@code true} if the
     * {@code CharSequence} is not {@code null}, its length is greater than
     * 0, and it contains at least one non-whitespace character.
     * <p><pre class="code">
     * StringUtils.hasText(null) = false
     * StringUtils.hasText("") = false
     * StringUtils.hasText(" ") = false
     * StringUtils.hasText("12345") = true
     * StringUtils.hasText(" 12345 ") = true
     * </pre>
     * @param input the {@code CharSequence} to check (may be {@code null})
     * @return {@code true} if the {@code CharSequence} is not {@code null},
     * its length is greater than 0, and it does not contain whitespace only
     * @see #hasText(String)
     * @see Character#isWhitespace
     */
    public static boolean hasText(final String input) {
        return (input != null && !input.isEmpty() && containsText(input));
    }

    private static boolean containsText(final CharSequence sequence) {
        int length = sequence.length();
        for (int i = 0; i < length; i++) {
            if (!Character.isWhitespace(sequence.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tokenize the given {@code String} into a {@code String} array via a
     * {@link StringTokenizer}.
     * <p>The given {@code delimiters} string can consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character.
     * @param str the {@code String} to tokenize (potentially {@code null} or empty)
     * @param delimiters the delimiter characters, assembled as a {@code String}
     * (each of the characters is individually considered as a delimiter)
     * @param trimTokens trim the tokens via {@link String#trim()}
     * @param ignoreEmptyTokens omit empty tokens from the result array
     * (only applies to tokens that are empty after trimming; StringTokenizer
     * will not consider subsequent delimiters as token in the first place).
     * @return an array of the tokens
     * @see java.util.StringTokenizer
     * @see String#trim()
     */
    public static String[] tokenizeToStringArray(String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

        if (str == null) {
            return EMPTY_STRING_ARRAY;
        }

        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0) {
                tokens.add(token);
            }
        }
        return toStringArray(tokens);
    }

    /**
     * Copy the given {@link Collection} into a {@code String} array.
     * <p>The {@code Collection} must contain {@code String} elements only.
     * @param collection the {@code Collection} to copy
     * (potentially {@code null} or empty)
     * @return the resulting {@code String} array
     */
    private static String[] toStringArray(Collection<String> collection) {
        return (!(collection == null || collection.isEmpty()) ? collection.toArray(EMPTY_STRING_ARRAY) : EMPTY_STRING_ARRAY);
    }

}
