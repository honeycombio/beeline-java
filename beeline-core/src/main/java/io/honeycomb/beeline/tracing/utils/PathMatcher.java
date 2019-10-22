package io.honeycomb.beeline.tracing.utils;

/**
 * Strategy for path matching.
 */
public interface PathMatcher {

    /**
     * Return <code>true</code> if the given <code>path</code> matches the given <code>pattern</code>, otherwise
     * <code>false</code>.
     *
     * @param pattern the pattern to match against
     * @param path  the path to match
     * @return whether the path matched the pattern
     */
    boolean match(String pattern, String path);
}
