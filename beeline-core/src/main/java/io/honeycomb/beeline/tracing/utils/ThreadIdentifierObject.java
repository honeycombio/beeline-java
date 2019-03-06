package io.honeycomb.beeline.tracing.utils;

/**
 * Simple class that can be used to identify whether execution is still on the same thread.
 */
public final class ThreadIdentifierObject {
    private static final ThreadLocal<ThreadIdentifierObject> currentId
        = ThreadLocal.withInitial(ThreadIdentifierObject::new);

    private ThreadIdentifierObject() {
        // helper class that uses thread-locals
    }

    /**
     * On a given thread, this will always return the same instance. This object instance will be unique to that
     * thread, meaning that object equality can be exploited to check whether execution has moved to another thread
     * (use {@link #isFromCurrentThread(ThreadIdentifierObject)} for that).
     *
     * @return this thread's identifier object.
     */
    public static ThreadIdentifierObject getCurrentThreadId() {
        return currentId.get();
    }

    /**
     * Checks whether the argument is the same instance as the current thread's identifier object and thus checks if
     * execution has moved to a different thread.
     *
     * @param originalId to use to check if this is still the same thread.
     * @return true if the argument is the same instance as the current thread's identifier object.
     */
    public static boolean isFromCurrentThread(final ThreadIdentifierObject originalId) {
        return originalId == currentId.get();
    }
}
