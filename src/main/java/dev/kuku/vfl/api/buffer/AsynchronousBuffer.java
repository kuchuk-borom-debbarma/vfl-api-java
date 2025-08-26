package dev.kuku.vfl.api.buffer;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsynchronousBuffer implements VFLBuffer {
    private static final Logger log = LoggerFactory.getLogger(AsynchronousBuffer.class);
    private final int bufferSize;
    private final int flushTimeout;
    private final ExecutorService flushExecutor;
    private final ScheduledExecutorService periodicFlushHandler;
    private final VFLFlushHandler flushHandler;

    private final List<Block> blocks = new ArrayList<>();
    private final List<BlockLog> logs = new ArrayList<>();
    private final Map<String, Long> blockEntered = new HashMap<>();
    private final Map<String, Long> blockExited = new HashMap<>();
    private final Map<String, Long> blockReturned = new HashMap<>();
    private int totalSize = 0;
    private volatile boolean flushingAll = false;

    public AsynchronousBuffer(int bufferSize, int flushInterval, int flushTimeout, ExecutorService flushExecutor, ScheduledExecutorService periodicFlushHandler, VFLFlushHandler flushHandler) {
        this.bufferSize = bufferSize;
        this.flushTimeout = flushTimeout;
        this.flushExecutor = flushExecutor;
        this.periodicFlushHandler = periodicFlushHandler;
        this.flushHandler = flushHandler;

        periodicFlushHandler.scheduleWithFixedDelay(() -> {
            if (flushingAll) {
                return; // Skip periodic flush during manual flush
            }
            synchronized (this) {
                var toFlush = copyContentsAndClear();
                flushAll(toFlush);
            }
        }, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
    }

    private synchronized FlushData copyContentsAndClear() {
        List<Block> blocks = new ArrayList<>(this.blocks);
        List<BlockLog> logs = new ArrayList<>(this.logs);
        Map<String, Long> blockEntered = new HashMap<>(this.blockEntered);
        Map<String, Long> blockExited = new HashMap<>(this.blockExited);
        Map<String, Long> blockReturned = new HashMap<>(this.blockReturned);

        this.blocks.clear();
        this.logs.clear();
        this.blockEntered.clear();
        this.blockExited.clear();
        this.blockReturned.clear();
        this.totalSize = 0;

        return new FlushData(blocks, logs, blockEntered, blockExited, blockReturned);
    }

    private void flushIfFull() {
        if (flushingAll) {
            return; // Don't add new tasks during manual flush
        }

        FlushData flushData = null;
        synchronized (this) {
            if (totalSize >= bufferSize) {
                flushData = copyContentsAndClear();
            }
        }

        if (flushData != null) {
            FlushData finalFlushData = flushData;
            flushExecutor.execute(() -> flushAll(finalFlushData));
        }
    }

    private void flushAll(FlushData toFlush) {
        if (!toFlush.blocks.isEmpty()) {
            flushHandler.flushBlocks(toFlush.blocks);
        }
        if (!toFlush.logs.isEmpty()) {
            flushHandler.flushLogs(toFlush.logs);
        }
        if (!toFlush.blockEntered.isEmpty()) {
            flushHandler.flushBlockEntered(toFlush.blockEntered);
        }
        if (!toFlush.blockExited.isEmpty()) {
            flushHandler.flushBlockExited(toFlush.blockExited);
        }
        if (!toFlush.blockReturned.isEmpty()) {
            flushHandler.flushBlockReturned(toFlush.blockReturned);
        }
    }

    @Override
    public void pushLog(BlockLog log) {
        synchronized (this) {
            logs.add(log);
            totalSize++;
        }
        flushIfFull();
    }

    @Override
    public void pushBlock(Block block) {
        synchronized (this) {
            blocks.add(block);
            totalSize++;
        }
        flushIfFull();
    }

    @Override
    public void pushBlockReturned(String blockId) {
        synchronized (this) {
            blockReturned.put(blockId, Instant.now().toEpochMilli());
            totalSize++;
        }
        flushIfFull();
    }

    @Override
    public void pushBlockEntered(String blockId) {
        synchronized (this) {
            blockEntered.put(blockId, Instant.now().toEpochMilli());
            totalSize++;
        }
        flushIfFull();
    }

    @Override
    public void pushBlockExited(String blockId) {
        synchronized (this) {
            blockExited.put(blockId, Instant.now().toEpochMilli());
            totalSize++;
        }
        flushIfFull();
    }

    @Override
    public void flush() {
        flushingAll = true;

        // Flush any remaining data in the buffer
        FlushData finalFlushData;
        synchronized (this) {
            finalFlushData = copyContentsAndClear();
        }

        // Submit final flush task if there's data
        if (!isEmpty(finalFlushData)) {
            flushExecutor.submit(() -> flushAll(finalFlushData));
        }

        // Wait for all tasks to complete with timeout
        long startTime = System.currentTimeMillis();
        while (hasActiveTasks()) {
            if (System.currentTimeMillis() - startTime > flushTimeout) {
                log.error("Flush timeout reached - some tasks may not have completed");
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Flush interrupted");
                break;
            }
        }

        flushingAll = false;
    }

    private boolean hasActiveTasks() {
        // Try ThreadPoolExecutor first for more accurate info
        if (flushExecutor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) flushExecutor;
            return tpe.getActiveCount() > 0 || tpe.getQueue().size() > 0;
        }
        // Fallback: assume no active tasks if we can't check
        // This is not ideal but works for any ExecutorService
        return false;
    }

    @Override
    public void close() {
        try {
            periodicFlushHandler.shutdown();
            if (!periodicFlushHandler.awaitTermination(flushTimeout, TimeUnit.MILLISECONDS)) {
                periodicFlushHandler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to close periodic flush handler: {}", e.getMessage());
        }

        try {
            if (!flushExecutor.awaitTermination(flushTimeout, TimeUnit.MILLISECONDS)) {
                log.error("Flush executor did not terminate within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to close flush executor: {}", e.getMessage());
        }
    }

    private boolean isEmpty(FlushData flushData) {
        return flushData.blocks.isEmpty() &&
               flushData.logs.isEmpty() &&
               flushData.blockEntered.isEmpty() &&
               flushData.blockExited.isEmpty() &&
               flushData.blockReturned.isEmpty();
    }

    private static class FlushData {
        private final List<Block> blocks;
        private final List<BlockLog> logs;
        private final Map<String, Long> blockEntered;
        private final Map<String, Long> blockExited;
        private final Map<String, Long> blockReturned;

        private FlushData(List<Block> blocks, List<BlockLog> logs, Map<String, Long> blockEntered, Map<String, Long> blockExited, Map<String, Long> blockReturned) {
            this.blocks = blocks;
            this.logs = logs;
            this.blockEntered = blockEntered;
            this.blockExited = blockExited;
            this.blockReturned = blockReturned;
        }
    }
}