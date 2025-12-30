package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TiredExecutor - Thread pool with fatigue-based scheduling.
 * 
 * Design:
 * - Maintains a pool of TiredThread workers
 * - Uses a PriorityBlockingQueue (min-heap) to always assign work to the LEAST fatigued thread
 * - Tracks in-flight tasks with an AtomicInteger counter
 * - Supports blocking until all submitted tasks complete
 * 
 * Scheduling Policy:
 * - Threads accumulate fatigue based on how long they work
 * - The PriorityBlockingQueue automatically orders threads by fatigue (min first)
 * - When a task completes, the worker is returned to the queue with updated fatigue
 * - This naturally load-balances: less-worked threads get more tasks
 * 
 * Thread Safety:
 * - PriorityBlockingQueue is thread-safe for add/take operations
 * - AtomicInteger for lock-free counter updates
 * - synchronized + wait/notifyAll for blocking on task completion
 * - Critical: inFlight is incremented BEFORE task submission to prevent race condition
 */
public class TiredExecutor {

    private final TiredThread[] workers;           // All worker threads
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();  // Idle workers, ordered by fatigue
    private final AtomicInteger inFlight = new AtomicInteger(0);  // Count of tasks currently executing

    /**
     * Creates a new executor with the specified number of worker threads.
     * Each worker gets a random fatigue factor between 0.5 and 1.5.
     * 
     * @param numThreads Number of worker threads to create
     * @throws IllegalArgumentException if numThreads <= 0
     */
    public TiredExecutor(int numThreads) {
        if (numThreads <= 0) throw new IllegalArgumentException("numThreads must be positive");

        TiredThread[] tmp = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            // Random fatigue factor in range [0.5, 1.5)
            // Higher factor means the thread gets tired faster
            double ff = 0.5 + Math.random(); // [0.5, 1.5)
            TiredThread t = new TiredThread(i, ff);
            tmp[i] = t;
            idleMinHeap.add(t);  // Add to idle pool
            t.start();           // Start the worker thread
        }
        this.workers = tmp;
    }

    /**
     * Submits a single task for execution by the least fatigued idle worker.
     * 
     * Implementation:
     * 1. Take the least fatigued worker from the min-heap (blocks if none available)
     * 2. Wrap the task to return the worker to the pool when done
     * 3. Increment inFlight BEFORE submission (critical for correctness)
     * 4. Submit the wrapped task to the worker
     * 
     * Thread Safety - Why increment BEFORE submission:
     * If we incremented after newTask(), a fast task could:
     * 1. Complete and decrement inFlight (now -1 if we hadn't incremented)
     * 2. Call notifyAll
     * 3. Then we increment (now 0, but too late - waiter already woken incorrectly)
     * 
     * @param task The runnable task to execute
     */
    public void submit(Runnable task) {
        TiredThread worker;
        try {
            // Take blocks until a worker is available
            // PriorityBlockingQueue returns the minimum (least fatigued) worker
            worker = idleMinHeap.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        
        // Wrap the task to handle completion: return worker to pool and update counter
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                // Return worker to idle pool (with updated fatigue from TiredThread)
                idleMinHeap.add(worker);
                
                // Decrement counter and notify if all tasks done
                int remaining = inFlight.decrementAndGet();
                if (remaining == 0) {
                    synchronized (this) {
                        this.notifyAll();  // Wake up any thread waiting in submitAll
                    }
                }
            }
        };
        
        // CRITICAL: Increment BEFORE submission to prevent race condition
        // where task completes before increment, causing inFlight to go negative
        inFlight.incrementAndGet();
        try {
            worker.newTask(wrapped);
        } catch (IllegalStateException e) {
            // newTask failed (e.g., worker shut down) - revert and cleanup
            inFlight.decrementAndGet();
            idleMinHeap.add(worker);
            throw e;
        }
    }

    /**
     * Submits all tasks and blocks until they all complete.
     * 
     * Implementation:
     * - Holds synchronized lock during both submission AND waiting
     * - This prevents a race where tasks complete and notify before we call wait()
     * - Uses wait/notifyAll pattern to efficiently block until inFlight reaches 0
     * 
     * @param tasks Collection of tasks to execute
     */
    public void submitAll(Iterable<Runnable> tasks) {
        // Synchronize before submitting to avoid race condition where tasks
        // complete and notify before we enter the wait block
        synchronized (this) {
            for (Runnable task : tasks) {
                submit(task);
            }
            
            // Wait until all tasks complete (inFlight == 0)
            while (inFlight.get() > 0) {
                try {
                    this.wait();  // Releases lock, waits for notifyAll, re-acquires lock
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Gracefully shuts down the executor.
     * 
     * Implementation:
     * 1. Wait for all in-flight tasks to complete
     * 2. Send poison pill (shutdown signal) to each worker
     * 3. Join all worker threads to ensure clean termination
     * 
     * @throws InterruptedException if interrupted while waiting
     */
    public void shutdown() throws InterruptedException {
    // Wait for all in-flight tasks to complete
    synchronized (this) {
        while (inFlight.get() > 0) {
            this.wait();
        }
    }
    
    // Send poison pill to each worker
    // This special task signals the worker to exit its run loop
    for (TiredThread worker : workers) {
        worker.shutdown();
    }
    
    // Join all worker threads to ensure they've terminated
    for (TiredThread worker : workers) {
        worker.join();
    }
    }

    /**
     * Generates a report of all workers' current status.
     * Useful for debugging and monitoring.
     * 
     * @return Formatted string with each worker's stats
     */
    public synchronized String getWorkerReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("Worker Report:\n");
    sb.append("=============\n");
    
    for (TiredThread worker : workers) {
        sb.append(String.format("Worker %d: fatigue=%.2f, timeUsed=%.2fms, timeIdle=%.2fms, busy=%b\n",
            worker.getWorkerId(),
            worker.getFatigue(),
            worker.getTimeUsed(),
            worker.getTimeIdle(),
            worker.isBusy()));
    }
    
    return sb.toString();
    }
}
