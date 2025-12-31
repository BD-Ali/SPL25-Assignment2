package scheduling;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TiredExecutorTest {

    @Test
    void ctor_nonPositiveThreads_throws() {
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(-1));
    }

    @Test
    void submitAll_runsAllTasksExactlyOnce() throws InterruptedException {
        TiredExecutor ex = new TiredExecutor(3);
        try {
            AtomicInteger counter = new AtomicInteger(0);

            List<Runnable> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) tasks.add(counter::incrementAndGet);

            ex.submitAll(tasks);

            assertEquals(100, counter.get());
        } finally {
            ex.shutdown();
        }
    }

    @Test
    void submitAll_blocksUntilTasksFinish() throws Exception {
        TiredExecutor ex = new TiredExecutor(2);
        try {
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);

            List<Runnable> tasks = List.of(
                    () -> {
                        started.countDown();
                        try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    },
                    () -> {
                        started.countDown();
                        try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
            );

            Thread t = new Thread(() -> ex.submitAll(tasks));
            t.start();

            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(t.isAlive(), "submitAll should still be blocked until tasks are released");

            release.countDown();

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> t.join());
        } finally {
            ex.shutdown();
        }
    }

    @Test
    void shutdown_terminatesPromptlyAfterWork() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            TiredExecutor ex = new TiredExecutor(2);
            try {
                AtomicInteger x = new AtomicInteger(0);
                ex.submitAll(List.of(
                        () -> { for (int i = 0; i < 200_000; i++) x.incrementAndGet(); },
                        () -> { for (int i = 0; i < 200_000; i++) x.incrementAndGet(); }
                ));
            } finally {
                ex.shutdown();
            }
        });
    }
}
