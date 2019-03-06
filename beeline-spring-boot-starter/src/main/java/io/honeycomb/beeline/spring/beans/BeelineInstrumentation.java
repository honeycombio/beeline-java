package io.honeycomb.beeline.spring.beans;

/**
 * Interface for identifying instrumentations within the Spring context.
 */
public interface BeelineInstrumentation {
    /**
     * A simple human-readable name to identify a particular instrumentation.
     *
     * @return the name.
     */
    String getName();
}
