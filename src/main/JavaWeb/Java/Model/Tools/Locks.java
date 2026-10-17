package Model.Tools;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class Locks {

    private static final Object lock = new Object();

    private static final ConcurrentHashMap<java.lang.String, ReentrantLock> concurrentHashMapLocks = new ConcurrentHashMap<>();

    public static Object getLock() {
        return lock;
    }

    public static ReentrantLock getConcurrentHashMapLock(java.lang.String key) {
        return concurrentHashMapLocks.computeIfAbsent(key, k -> new ReentrantLock());
    }
    public static void removeConcurrentHashMapLock(java.lang.String key) {
        concurrentHashMapLocks.computeIfPresent(key, (k, lock) -> lock.hasQueuedThreads() ? lock : null);
    }

    public static void executeWithLock(java.lang.String key, Runnable task, int timeLimit) {
        ReentrantLock reentrantLock = getConcurrentHashMapLock(key);
        boolean lockAcquired = false;
        try {
            lockAcquired = reentrantLock.tryLock(timeLimit, TimeUnit.MILLISECONDS);
            if (lockAcquired) {
                task.run();
            }
        } catch (InterruptedException interruptedException) {
            System.out.println("An interruptedException is occurred! (Model.Tools.Locks.java)");
        } finally {
            if (lockAcquired) {
                reentrantLock.unlock();
            }
        }
    }

    public static void executeTask(java.lang.String key, Runnable task) {
        ReentrantLock reentrantLock = getConcurrentHashMapLock(key);
        reentrantLock.lock();
        try {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Task is terminated! (Model.Tools.Locks.java)");
            }
            task.run();
        } catch (Exception exception) {
            System.out.println("Task execution exception: " + exception.getMessage() + " (Model.Tools.Locks.java)");
        } finally {
            reentrantLock.unlock();
        }
    }

}
