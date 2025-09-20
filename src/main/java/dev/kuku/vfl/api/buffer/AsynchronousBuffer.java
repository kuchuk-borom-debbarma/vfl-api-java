package dev.kuku.vfl.api.buffer;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous buffer that can flush data in parallel without blocking the main thread.
 * <p>
 * This implementation supports:
 * <ul>
 *     <li>Concurrent flushes without blocking operations</li>
 *     <li>Automatic flushing when buffer reaches capacity</li>
 *     <li>Periodic flushing at configured intervals</li>
 *     <li>Force flush with timeout for graceful shutdown</li>
 * </ul>
 * <p>
 * Thread Safety: This class is thread-safe and designed for concurrent access.
 */
public class AsynchronousBuffer implements VFLBuffer {

    private static final Logger log = LoggerFactory.getLogger(AsynchronousBuffer.class);
    private static final int FORCE_FLUSH_POLL_INTERVAL_MS = 50;

    // Configuration
    private final int bufferSize;
    private final int flushTimeoutMs;
    private final ExecutorService flushExecutor;
    private final ScheduledExecutorService periodicFlushScheduler;
    private final VFLFlushHandler flushHandler;

    // Buffer state - all access must be synchronized
    private final List<Block> blocks = new ArrayList<>();
    private final List<BlockLog> logs = new ArrayList<>();
    private final Map<String, Long> blockEntered = new HashMap<>();
    private final Map<String, Long> blockExited = new HashMap<>();
    private final Map<String, Long> blockReturned = new HashMap<>();
    private int totalSize = 0;

    // Flush operation tracking
    private final AtomicInteger pendingFlushCount = new AtomicInteger(0);

    /**
     * Creates a new AsynchronousBuffer with the specified configuration.
     *
     * @param bufferSize             Maximum number of items to buffer before auto-flush
     * @param flushIntervalMs        Interval between periodic flushes in milliseconds
     * @param flushTimeoutMs         Maximum time to wait for flush operations during force flush
     * @param flushExecutor          Executor service for running flush operations
     * @param periodicFlushScheduler Scheduled executor for periodic flushes
     * @param flushHandler           Handler that performs the actual flush operations
     */
    public AsynchronousBuffer(
            int bufferSize,
            int flushIntervalMs,
            int flushTimeoutMs,
            ExecutorService flushExecutor,
            ScheduledExecutorService periodicFlushScheduler,
            VFLFlushHandler flushHandler) {

        this.bufferSize = bufferSize;
        this.flushTimeoutMs = flushTimeoutMs;
        this.flushExecutor = flushExecutor;
        this.periodicFlushScheduler = periodicFlushScheduler;
        this.flushHandler = flushHandler;

        startPeriodicFlushing(flushIntervalMs);
    }

    /**
     * Starts the periodic flush task that runs at fixed intervals.
     */
    private void startPeriodicFlushing(int flushIntervalMs) {
        periodicFlushScheduler.scheduleWithFixedDelay(
                this::performPeriodicFlush,
                flushIntervalMs,
                flushIntervalMs,
                TimeUnit.MILLISECONDS
        );
        log.debug("Started periodic flushing with interval {} ms", flushIntervalMs);
    }

    /**
     * Performs a periodic flush if there's data to flush.
     */
    private void performPeriodicFlush() {
        try {
            FlushData dataToFlush = copyBufferContentsAndClear();
            if (!dataToFlush.isEmpty()) {
                submitFlushTask(dataToFlush);
                log.debug("Performed periodic flush with {} items", dataToFlush.getTotalSize());
            }
        } catch (Exception e) {
            log.error("Error during periodic flush", e);
        }
    }

    /**
     * Copies all buffer contents and clears the buffer atomically.
     * Must be called within synchronized block.
     *
     * @return FlushData containing copied buffer contents
     */
    private synchronized FlushData copyBufferContentsAndClear() {
        FlushData data = new FlushData(
                new ArrayList<>(blocks),
                new ArrayList<>(logs),
                new HashMap<>(blockEntered),
                new HashMap<>(blockExited),
                new HashMap<>(blockReturned)
        );

        clearBuffer();
        return data;
    }

    /**
     * Clears all buffer contents. Must be called within synchronized block.
     */
    private void clearBuffer() {
        blocks.clear();
        logs.clear();
        blockEntered.clear();
        blockExited.clear();
        blockReturned.clear();
        totalSize = 0;
    }

    /**
     * Checks if buffer is full and flushes if necessary.
     * This method is non-blocking - flush happens asynchronously.
     */
    private void flushIfBufferFull() {
        FlushData dataToFlush = null;

        synchronized (this) {
            if (totalSize >= bufferSize) {
                dataToFlush = copyBufferContentsAndClear();
            }
        }

        if (dataToFlush != null && !dataToFlush.isEmpty()) {
            submitFlushTask(dataToFlush);
            log.debug("Auto-flushed buffer containing {} items", dataToFlush.getTotalSize());
        }
    }

    /**
     * Submits a flush task to the executor and tracks it.
     */
    private void submitFlushTask(FlushData dataToFlush) {
        pendingFlushCount.incrementAndGet();
        flushExecutor.execute(() -> executeFlushOperation(dataToFlush));
    }

    /**
     * Executes the actual flush operation and handles completion tracking.
     */
    private void executeFlushOperation(FlushData dataToFlush) {
        try {
            performFlushOperations(dataToFlush);
            log.debug("Successfully flushed {} items", dataToFlush.getTotalSize());
        } catch (Exception e) {
            log.error("Error during flush operation", e);
            // Don't rethrow - we don't want to kill the executor thread
        } finally {
            pendingFlushCount.decrementAndGet();
        }
    }

    /**
     * Performs the individual flush operations in the correct order.
     * Order is important for data consistency.
     */
    private void performFlushOperations(FlushData dataToFlush) {
        if (!dataToFlush.blocks.isEmpty()) {
            flushHandler.flushBlocks(dataToFlush.blocks);
        }
        if (!dataToFlush.logs.isEmpty()) {
            flushHandler.flushLogs(dataToFlush.logs);
        }
        if (!dataToFlush.blockEntered.isEmpty()) {
            flushHandler.flushBlockEntered(dataToFlush.blockEntered);
        }
        if (!dataToFlush.blockExited.isEmpty()) {
            flushHandler.flushBlockExited(dataToFlush.blockExited);
        }
        if (!dataToFlush.blockReturned.isEmpty()) {
            flushHandler.flushBlockReturned(dataToFlush.blockReturned);
        }
    }

    // VFLBuffer interface implementations

    @Override
    public void pushLog(BlockLog log) {
        synchronized (this) {
            logs.add(log);
            totalSize++;
        }
        flushIfBufferFull();
    }

    @Override
    public void pushBlock(Block block) {
        synchronized (this) {
            blocks.add(block);
            totalSize++;
        }
        flushIfBufferFull();
    }

    @Override
    public void pushBlockReturned(String blockId, long time) {
        synchronized (this) {
            blockReturned.put(blockId, time);
            totalSize++;
        }
        flushIfBufferFull();
    }

    @Override
    public void pushBlockEntered(String blockId, long time) {
        synchronized (this) {
            blockEntered.put(blockId, time);
            totalSize++;
        }
        flushIfBufferFull();
    }

    @Override
    public void pushBlockExited(String blockId, long time) {
        synchronized (this) {
            blockExited.put(blockId, time);
            totalSize++;
        }
        flushIfBufferFull();
    }

    @Override
    public void forceFlush() {
        log.debug("Force flush initiated");

        // Step 1: Flush any remaining data in the buffer
        FlushData remainingData = copyBufferContentsAndClear();
        if (!remainingData.isEmpty()) {
            submitFlushTask(remainingData);
            log.debug("Submitted final flush task with {} items", remainingData.getTotalSize());
        }

        // Step 2: Wait for all pending flush operations to complete
        if (!waitForPendingFlushesToComplete()) {
            log.warn("Force flush timed out after {} ms with {} operations still pending",
                    flushTimeoutMs, pendingFlushCount.get());
            return;
        }

        log.debug("Force flush completed successfully");
    }

    /**
     * Waits for all pending flush operations to complete within the timeout.
     *
     * @return true if all operations completed, false if timed out
     */
    private boolean waitForPendingFlushesToComplete() {
        if (pendingFlushCount.get() == 0) {
            return true; // Nothing to wait for
        }

        long startTime = System.currentTimeMillis();
        log.debug("Waiting for {} pending flush operations to complete", pendingFlushCount.get());

        while (pendingFlushCount.get() > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= flushTimeoutMs) {
                return false; // Timed out
            }

            long remainingTime = flushTimeoutMs - elapsed;
            long sleepTime = Math.min(FORCE_FLUSH_POLL_INTERVAL_MS, remainingTime);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Force flush interrupted", e);
            }
        }

        return true; // All operations completed
    }
    
    /**
     * Returns the number of pending flush operations.
     * Useful for monitoring and testing.
     *
     * @return number of flush operations currently being executed
     */
    public int getPendingFlushCount() {
        return pendingFlushCount.get();
    }

    /**
     * Returns the current buffer size.
     * Useful for monitoring and testing.
     *
     * @return current number of items in the buffer
     */
    public synchronized int getCurrentBufferSize() {
        return totalSize;
    }

    /**
     * Immutable data class that holds all buffer contents for flushing.
     * This allows us to pass buffer contents between threads safely.
     */
    private static class FlushData {
        private final List<Block> blocks;
        private final List<BlockLog> logs;
        private final Map<String, Long> blockEntered;
        private final Map<String, Long> blockExited;
        private final Map<String, Long> blockReturned;

        FlushData(List<Block> blocks, List<BlockLog> logs,
                  Map<String, Long> blockEntered, Map<String, Long> blockExited,
                  Map<String, Long> blockReturned) {
            this.blocks = blocks;
            this.logs = logs;
            this.blockEntered = blockEntered;
            this.blockExited = blockExited;
            this.blockReturned = blockReturned;
        }

        boolean isEmpty() {
            return blocks.isEmpty() && logs.isEmpty() && blockEntered.isEmpty()
                   && blockExited.isEmpty() && blockReturned.isEmpty();
        }

        int getTotalSize() {
            return blocks.size() + logs.size() + blockEntered.size()
                   + blockExited.size() + blockReturned.size();
        }
    }
}