package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public TiredExecutor(int numThreads) {
        if (numThreads <= 0) throw new IllegalArgumentException("numThreads must be positive");

        TiredThread[] tmp = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            double ff = 0.5 + Math.random(); // [0.5, 1.5)
            TiredThread t = new TiredThread(i, ff);
            tmp[i] = t;
            idleMinHeap.add(t);
            t.start();
        }
        this.workers = tmp;
    }

    public void submit(Runnable task) {
        TiredThread worker;
        try {
            worker = idleMinHeap.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        
        inFlight.incrementAndGet();
        
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                idleMinHeap.add(worker);
                int remaining = inFlight.decrementAndGet();
                if (remaining == 0) {
                    synchronized (this) {
                        this.notifyAll();
                    }
                }
            }
        };
        
        worker.newTask(wrapped);
    }

    public void submitAll(Iterable<Runnable> tasks) {
        for (Runnable task : tasks) {
        submit(task);
    }
    
    // Wait until all tasks complete
    synchronized (this) {
        while (inFlight.get() > 0) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
    }

    public void shutdown() throws InterruptedException {
    // Wait for all in-flight tasks to complete
    synchronized (this) {
        while (inFlight.get() > 0) {
            this.wait();
        }
    }
    
    // Send poison pill to each worker
    for (TiredThread worker : workers) {
        worker.shutdown();
    }
    
    // Join all worker threads
    for (TiredThread worker : workers) {
        worker.join();
    }
    }

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
