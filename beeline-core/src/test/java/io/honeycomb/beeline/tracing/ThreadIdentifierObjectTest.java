package io.honeycomb.beeline.tracing;

import io.honeycomb.beeline.tracing.utils.ThreadIdentifierObject;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;


public class ThreadIdentifierObjectTest {

    @Test
    public void checkNotNull() {
        assertThat(ThreadIdentifierObject.getCurrentThreadId()).isNotNull();
    }

    @Test
    public void checkThatTheIdObjectIsThreadBound() throws InterruptedException {
        final AtomicReference<ThreadIdentifierObject> threadId1 = new AtomicReference<>();
        final AtomicReference<ThreadIdentifierObject> threadId2 = new AtomicReference<>();

        // Run two threads in order to generate two different identifier objects
        final Thread thread1 = new Thread(() -> {
            final ThreadIdentifierObject firstID = ThreadIdentifierObject.getCurrentThreadId();
            final ThreadIdentifierObject secondID = ThreadIdentifierObject.getCurrentThreadId();
            // Check that multiple invocations from same thread return the same object
            threadId1.set(ThreadIdentifierObject.getCurrentThreadId());
            assertThat(firstID).isSameAs(secondID);
        });
        final Thread thread2 = new Thread(() -> {
            final ThreadIdentifierObject firstID = ThreadIdentifierObject.getCurrentThreadId();
            final ThreadIdentifierObject secondID = ThreadIdentifierObject.getCurrentThreadId();
            threadId2.set(ThreadIdentifierObject.getCurrentThreadId());
            assertThat(firstID).isSameAs(secondID);
        });

        // run threads
        thread1.start();
        thread2.start();

        // wait for them to finish (upper bound on wait just in case)
        thread1.join(10_0000);
        thread2.join(10_0000);

        // Check that the two threads produced different identifiers
        assertThat(threadId1.get()).isNotSameAs(threadId2.get());
    }
}
