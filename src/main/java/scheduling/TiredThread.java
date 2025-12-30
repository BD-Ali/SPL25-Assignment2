package scheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TiredThread - A worker thread that tracks its own fatigue level.
 * 
 * Design:
 * - Extends Thread and implements Comparable for use in PriorityBlockingQueue
 * - Each worker has a fatigue factor that determines how quickly it gets tired
 * - Fatigue = fatigueFactor * timeUsed (higher means more tired)
 * - Uses a single-slot BlockingQueue for task handoff from executor
 * 
 * Lifecycle:
 * 1. Created by TiredExecutor with a unique ID and random fatigue factor
 * 2. Runs in a loop waiting for tasks via the handoff queue
 * 3. Executes tasks and updates timing statistics
 * 4. Receives POISON_PILL to signal shutdown
 * 
 * Thread Safety:
 * - AtomicBoolean for alive and busy flags
 * - AtomicLong for timing statistics (timeUsed, timeIdle, idleStartTime)
 * - ArrayBlockingQueue for thread-safe task handoff
 * - Stats are updated BEFORE busy flag is cleared for consistent reads
 * 
 * Ordering (for PriorityBlockingQueue):
 * - Compared by fatigue (lower = higher priority = gets assigned work first)
 * - Ties broken by worker ID for determinism
 */
public class TiredThread extends Thread implements Comparable<TiredThread> {

    // Poison pill pattern: special marker task that signals the worker to shut down
    // Using a no-op Runnable ensures it won't throw when accidentally run
    private static final Runnable POISON_PILL = () -> {}; // Special task to signal shutdown

    private final int id;               // Worker index assigned by the executor
    private final double fatigueFactor; // Multiplier for fatigue calculation (0.5-1.5)

    // Alive flag: when false, worker should exit after current task
    private final AtomicBoolean alive = new AtomicBoolean(true); // Indicates if the worker should keep running

    // Single-slot handoff queue; executor will put tasks here
    // Capacity of 1 means: at most one pending task (the one being executed)
    // Non-blocking offer() is used to detect if worker isn't ready
    private final BlockingQueue<Runnable> handoff = new ArrayBlockingQueue<>(1);

    // Busy flag: true when executing a task, false when idle
    // Used by executor to track worker state (though primarily for debugging/reporting)
    private final AtomicBoolean busy = new AtomicBoolean(false); // Indicates if the worker is currently executing a task

    // Timing statistics (in nanoseconds)
    private final AtomicLong timeUsed = new AtomicLong(0);      // Total time spent executing tasks
    private final AtomicLong timeIdle = new AtomicLong(0);      // Total time spent idle
    private final AtomicLong idleStartTime = new AtomicLong(0); // Timestamp when the worker became idle

    /**
     * Creates a new worker thread.
     * 
     * @param id Unique identifier for this worker
     * @param fatigueFactor Multiplier for fatigue (higher = gets tired faster)
     */
    public TiredThread(int id, double fatigueFactor) {
        this.id = id;
        this.fatigueFactor = fatigueFactor;
        this.idleStartTime.set(System.nanoTime());  // Start as idle
        setName(String.format("FF=%.2f", fatigueFactor));  // Thread name for debugging
    }

    /** Returns the worker's unique ID */
    public int getWorkerId() {
        return id;
    }

    /**
     * Calculates current fatigue level.
     * Fatigue = fatigueFactor × timeUsed
     * 
     * Workers with higher fatigue factors get tired faster for the same work.
     * Workers with more timeUsed have done more work and are more tired.
     * 
     * @return Current fatigue value (used for priority ordering)
     */
    public double getFatigue() {
        return fatigueFactor * timeUsed.get();
    }

    /** Returns true if worker is currently executing a task */
    public boolean isBusy() {
        return busy.get();
    }

    /** Returns total time spent executing tasks (nanoseconds) */
    public long getTimeUsed() {
        return timeUsed.get();
    }

    /** Returns total time spent idle (nanoseconds) */
    public long getTimeIdle() {
        return timeIdle.get();
    }

    /**
     * Assign a task to this worker.
     * This method is NON-BLOCKING: if the worker is not ready to accept a task,
     * it throws IllegalStateException immediately rather than waiting.
     * 
     * The executor should only call this after taking the worker from the idle pool,
     * so the queue should always be empty and ready to accept.
     * 
     * @param task The task to execute
     * @throws IllegalArgumentException if task is null
     * @throws IllegalStateException if worker's queue is full (not ready)
     */
    public void newTask(Runnable task) {
       if (task == null) throw new IllegalArgumentException("task is null");

       // Non-blocking requirement: if queue is full, reject
       // offer() returns false immediately if queue is full
       boolean ok = handoff.offer(task);
       if (!ok) {
            throw new IllegalStateException("Worker " + id + " is not ready to accept a task");
        }
    }

    /**
     * Request this worker to stop after finishing current task.
     * 
     * Implementation:
     * 1. Set alive=false so the run loop will exit
     * 2. Send POISON_PILL to wake up the worker if it's blocked on take()
     * 
     * Uses blocking put() for poison pill to ensure reliable delivery.
     * If the queue is full (worker has a task), put() waits until the worker
     * takes that task and creates space.
     */
    public void shutdown() {
        alive.set(false);
        // Must reliably deliver poison pill - use blocking put
        try {
            handoff.put(POISON_PILL);
        } catch (InterruptedException e) {
            // If interrupted, force interrupt on worker thread
            this.interrupt();
        }
    }

    /**
     * Main worker loop: wait for tasks, execute them, update stats.
     * 
     * The loop continues while alive=true.
     * Each iteration:
     * 1. Block on take() waiting for a task
     * 2. If task is POISON_PILL, exit
     * 3. Mark busy=true, record idle time since last task
     * 4. Execute the task
     * 5. Update timeUsed with execution duration
     * 6. Set busy=false LAST (critical for consistent reads)
     * 
     * Statistics Ordering:
     * We update timeUsed and idleStartTime BEFORE setting busy=false.
     * This ensures that when another thread observes busy=false,
     * the statistics are already updated and consistent.
     */
    @Override
    public void run() {
        while (alive.get()) {
            try {
                // Wait for task (blocks until task available)
                Runnable task = handoff.take();

                // Poison pill => exit the run loop
                if (task == POISON_PILL) {
                    break;
                }

                // Mark transition IDLE -> BUSY
                busy.set(true);
                long now = System.nanoTime();

                // Accumulate idle time since last idle start
                long idleStart = idleStartTime.get();
                if (idleStart != 0) {
                    timeIdle.addAndGet(Math.max(0L, now - idleStart));
                }

                // Execute the task and measure timeUsed
                long start = System.nanoTime();
                try {
                    task.run();
                } finally {
                    long end = System.nanoTime();
                    
                    // CRITICAL: Update stats BEFORE clearing busy flag
                    // This ensures that when busy=false is observed, stats are already updated
                    // Otherwise: reader could see busy=false but stale stats
                    timeUsed.addAndGet(Math.max(0L, end - start));
                    idleStartTime.set(System.nanoTime());
                    
                    // Set busy=false LAST to ensure consistent read ordering
                    // The stats (timeUsed, idleStartTime) are now up-to-date
                    busy.set(false);
                }

            } catch (InterruptedException e) {
                // Exit if interrupted during shutdown; otherwise keep interrupt status and loop
                if (!alive.get()) break;
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Compares workers by fatigue for PriorityBlockingQueue ordering.
     * Lower fatigue = higher priority (gets work first).
     * Ties are broken by ID for determinism.
     * 
     * @param o The other worker to compare to
     * @return Negative if this is less fatigued, positive if more, 0 if equal
     */
    @Override
    public int compareTo(TiredThread o) {
        int cmp = Double.compare(this.getFatigue(), o.getFatigue());
        if (cmp != 0) return cmp;
        return Integer.compare(this.id, o.id);  // Tie-breaker for determinism
    }
}