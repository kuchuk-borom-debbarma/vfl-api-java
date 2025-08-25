package dev.kuku.vfl.api.buffer;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Asynchronous buffer implementation that batches log and block data
 * and flushes it asynchronously using an {@link ExecutorService}.
 *
 * <p>This buffer supports periodic flushing at a fixed interval and
 * automatic flushing when the buffer reaches capacity.
 *
 * <p><b>Main features:</b>
 * <ul>
 *   <li>Thread-safe with optimized read-write locks</li>
 *   <li>Batches incoming logs, blocks, and events for efficient flushing</li>
 *   <li>Flushes buffered data asynchronously via a provided {@link ExecutorService}</li>
 *   <li>Periodically flushes at a configurable interval</li>
 *   <li>Falls back to synchronous flush if executor rejects tasks</li>
 *   <li>Atomic size tracking for fast buffer size checks</li>
 *   <li>Blocking flush that drains existing tasks without shutting down executors</li>
 *   <li>Parallel flush operations for maximum throughput</li>
 * </ul>
 */
public class AsyncBuffer implements VFLBuffer {
    private static final Logger log = LoggerFactory.getLogger(AsyncBuffer.class);
    private final VFLFlushHandler flushHandler;
    private final int flushSize;
    private final ExecutorService flushExecutor;
    private final ScheduledExecutorService periodicExecutor;
    private final int flushTimeout;
    private final int periodicFlushTimeMillisecond;

    // Read-write lock for thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    // Atomic counter for fast size checking
    private final AtomicInteger totalSize = new AtomicInteger(0);

    // Flush coordination
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final Object flushLock = new Object();

    // Periodic flush control
    private volatile ScheduledFuture<?> periodicFlushTask;

    // Buffer collections
    private final List<BlockLog> logsToFlush;
    private final List<Block> blocksToFlush;
    private final Map<String, Long> blockEnteredToFlush;
    private final Map<String, Long> blockExitedToFlush;
    private final Map<String, Long> blockReturnedToFlush;

    /**
     * Constructs an AsyncBuffer instance.
     *
     * @param bufferSize                   max number of buffered items before automatic flush
     * @param finalFlushTimeoutMillisecond max millis to wait for async flush tasks to complete on flush
     * @param periodicFlushTimeMillisecond interval in millis to trigger periodic flushes
     * @param flushHandler                 handler responsible for sending flushed data to destination
     * @param bufferFlushExecutor          executor for async flush task execution
     * @param periodicFlushExecutor        scheduled executor for periodic flush triggers
     */
    public AsyncBuffer(int bufferSize,
                       int finalFlushTimeoutMillisecond,
                       int periodicFlushTimeMillisecond,
                       VFLFlushHandler flushHandler,
                       ExecutorService bufferFlushExecutor,
                       ScheduledExecutorService periodicFlushExecutor) {
        this.flushHandler = flushHandler;
        this.flushSize = bufferSize;
        this.flushExecutor = bufferFlushExecutor;
        this.periodicExecutor = periodicFlushExecutor;
        this.flushTimeout = finalFlushTimeoutMillisecond;
        this.periodicFlushTimeMillisecond = periodicFlushTimeMillisecond;

        // Initialize collections with reasonable capacity to minimize resizing
        int initialCapacity = Math.max(16, bufferSize / 4);
        this.logsToFlush = new ArrayList<>(initialCapacity);
        this.blocksToFlush = new ArrayList<>(initialCapacity);
        this.blockEnteredToFlush = new HashMap<>(initialCapacity);
        this.blockExitedToFlush = new HashMap<>(initialCapacity);
        this.blockReturnedToFlush = new HashMap<>(initialCapacity);

        // Start periodic flushes
        startPeriodicFlush();
    }

    /**
     * Starts periodic flushing. Can be called to restart after stopping.
     */
    public void startPeriodicFlush() {
        if (periodicFlushTask == null || periodicFlushTask.isCancelled()) {
            periodicFlushTask = periodicExecutor.scheduleWithFixedDelay(
                    this::flushAllAsync,
                    periodicFlushTimeMillisecond,
                    periodicFlushTimeMillisecond,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Stops periodic flushing without shutting down the executor.
     */
    public void stopPeriodicFlush() {
        if (periodicFlushTask != null) {
            periodicFlushTask.cancel(false);
        }
    }

    @Override
    public void pushLog(BlockLog log) {
        writeLock.lock();
        try {
            logsToFlush.add(log);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlushAsync();
    }

    @Override
    public void pushBlock(Block block) {
        writeLock.lock();
        try {
            blocksToFlush.add(block);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlushAsync();
    }

    @Override
    public void pushBlockReturned(String blockId) {
        long timestamp = Instant.now().toEpochMilli();
        writeLock.lock();
        try {
            blockReturnedToFlush.put(blockId, timestamp);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlushAsync();
    }

    @Override
    public void pushBlockEntered(String blockId) {
        long timestamp = Instant.now().toEpochMilli();
        writeLock.lock();
        try {
            blockEnteredToFlush.put(blockId, timestamp);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlushAsync();
    }

    @Override
    public void pushBlockExited(String blockId) {
        long timestamp = Instant.now().toEpochMilli();
        writeLock.lock();
        try {
            blockExitedToFlush.put(blockId, timestamp);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlushAsync();
    }

    @Override
    public void flush() {
        // Set flush in progress flag to block new async flushes
        if (!flushInProgress.compareAndSet(false, true)) {
            // Another flush is already in progress, wait for it
            synchronized (flushLock) {
                while (flushInProgress.get()) {
                    try {
                        flushLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted waiting for flush", e);
                    }
                }
            }
            return;
        }

        try {
            // Temporarily stop periodic flushing during blocking flush
            boolean wasPeriodicActive = periodicFlushTask != null && !periodicFlushTask.isCancelled();
            if (wasPeriodicActive) {
                periodicFlushTask.cancel(false);
            }

            // Wait for all active async flush tasks to complete
            waitForActiveTasks();

            // Now perform final synchronous flush with all remaining data
            flushAllSync();

            // Restart periodic flushing if it was active
            if (wasPeriodicActive) {
                startPeriodicFlush();
            }

        } finally {
            // Re-enable async flushes
            flushInProgress.set(false);
            synchronized (flushLock) {
                flushLock.notifyAll();
            }
        }
    }

    private void waitForActiveTasks() {
        long startTime = System.currentTimeMillis();
        while (activeTasks.get() > 0) {
            if (System.currentTimeMillis() - startTime > flushTimeout) {
                throw new RuntimeException("Timeout waiting for active tasks to complete: " + flushTimeout + "ms");
            }
            try {
                Thread.sleep(10); // Small sleep to avoid busy waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for tasks", e);
            }
        }
    }

    private void flushAllAsync() {
        executeFlushAsync();
    }

    /**
     * Synchronous flush for blocking scenarios
     */
    private void flushAllSync() {
        FlushData data = createFlushSnapshot();
        if (data != null) {
            performParallelFlush(data);
        }
    }

    /**
     * Execute flush asynchronously, fall back to sync if executor unavailable
     */
    private void executeFlushAsync() {
        // Don't start new async flush if blocking flush is in progress
        if (flushInProgress.get()) {
            return;
        }

        FlushData data = createFlushSnapshot();
        if (data == null) {
            return;
        }

        if (flushExecutor.isShutdown()) {
            performParallelFlush(data);
            return;
        }

        try {
            // Increment active task counter
            activeTasks.incrementAndGet();

            flushExecutor.submit(() -> {
                try {
                    // Double-check flush status before processing
                    if (!flushInProgress.get()) {
                        performParallelFlush(data);
                    }
                } finally {
                    // Always decrement counter when task completes
                    activeTasks.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            // Decrement counter since task wasn't submitted
            activeTasks.decrementAndGet();
            log.warn("Task rejected by executor, performing synchronous flush", e);
            performParallelFlush(data);
        }
    }

    /**
     * Creates a snapshot of all buffered data and clears the buffers atomically
     */
    private @Nullable FlushData createFlushSnapshot() {
        writeLock.lock();
        try {
            if (totalSize.get() == 0) {
                return null;
            }

            FlushData data = new FlushData(
                    new ArrayList<>(this.logsToFlush),
                    new ArrayList<>(this.blocksToFlush),
                    new HashMap<>(this.blockEnteredToFlush),
                    new HashMap<>(this.blockExitedToFlush),
                    new HashMap<>(this.blockReturnedToFlush)
            );

            // Clear all collections
            this.blocksToFlush.clear();
            this.logsToFlush.clear();
            this.blockEnteredToFlush.clear();
            this.blockExitedToFlush.clear();
            this.blockReturnedToFlush.clear();
            totalSize.set(0);

            return data;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Performs parallel flush operations for maximum throughput.
     * All flush operations run concurrently and the method waits for all to complete.
     */
    private void performParallelFlush(FlushData data) {
        List<CompletableFuture<Void>> flushTasks = new ArrayList<>();

        try {
            // Submit all flush operations in parallel
            if (!data.blocks.isEmpty()) {
                flushTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        flushHandler.flushBlocks(data.blocks);
                    } catch (Exception e) {
                        log.error("Error flushing blocks", e);
                    }
                }, flushExecutor));
            }

            if (!data.logs.isEmpty()) {
                flushTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        flushHandler.flushLogs(data.logs);
                    } catch (Exception e) {
                        log.error("Error flushing logs", e);
                    }
                }, flushExecutor));
            }

            if (!data.blockEntered.isEmpty()) {
                flushTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        flushHandler.flushBlockEntered(data.blockEntered);
                    } catch (Exception e) {
                        log.error("Error flushing block entered events", e);
                    }
                }, flushExecutor));
            }

            if (!data.blockExited.isEmpty()) {
                flushTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        flushHandler.flushBlockExited(data.blockExited);
                    } catch (Exception e) {
                        log.error("Error flushing block exited events", e);
                    }
                }, flushExecutor));
            }

            if (!data.blockReturned.isEmpty()) {
                flushTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        flushHandler.flushBlockReturned(data.blockReturned);
                    } catch (Exception e) {
                        log.error("Error flushing block returned events", e);
                    }
                }, flushExecutor));
            }

            // Wait for all flush tasks to complete
            if (!flushTasks.isEmpty()) {
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        flushTasks.toArray(new CompletableFuture[0])
                );

                // Wait with timeout to prevent hanging
                try {
                    allTasks.get(flushTimeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    log.error("Flush operations timed out after {}ms", flushTimeout);
                    // Cancel remaining tasks
                    flushTasks.forEach(task -> task.cancel(true));
                    throw new RuntimeException("Flush operations timed out", e);
                }
            }

        } catch (Exception e) {
            log.error("Error during parallel flush operations", e);
            throw new RuntimeException("Parallel flush failed", e);
        }
    }

    private void checkAndFlushAsync() {
        if (totalSize.get() >= flushSize) {
            flushAllAsync();
        }
    }

    public int getCurrentSize() {
        return totalSize.get();
    }

    public boolean isEmpty() {
        return totalSize.get() == 0;
    }

    public int getActiveTasks() {
        return activeTasks.get();
    }

    public boolean isFlushInProgress() {
        return flushInProgress.get();
    }

    @Override
    public String toString() {
        return "AsyncBuffer{" +
               "flushSize=" + flushSize +
               ", flushTimeout=" + flushTimeout +
               ", currentSize=" + totalSize.get() +
               ", activeTasks=" + activeTasks.get() +
               ", flushInProgress=" + flushInProgress.get() +
               '}';
    }

    /**
     * Data class to hold flush snapshots
     */
    private static class FlushData {
        final List<BlockLog> logs;
        final List<Block> blocks;
        final Map<String, Long> blockEntered;
        final Map<String, Long> blockExited;
        final Map<String, Long> blockReturned;

        FlushData(List<BlockLog> logs, List<Block> blocks,
                  Map<String, Long> blockEntered, Map<String, Long> blockExited,
                  Map<String, Long> blockReturned) {
            this.logs = logs;
            this.blocks = blocks;
            this.blockEntered = blockEntered;
            this.blockExited = blockExited;
            this.blockReturned = blockReturned;
        }
    }
}
