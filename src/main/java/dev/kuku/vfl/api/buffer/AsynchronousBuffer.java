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
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous buffer that can flush data parallelly without blocking main thread. <br>
 * Multiple concurrent flushes are allowed.
 */
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

    public AsynchronousBuffer(int bufferSize, int flushInterval, int flushTimeout, ExecutorService flushExecutor, ScheduledExecutorService periodicFlushHandler, VFLFlushHandler flushHandler) {
        this.bufferSize = bufferSize;
        this.flushTimeout = flushTimeout;
        this.flushExecutor = flushExecutor;
        this.periodicFlushHandler = periodicFlushHandler;
        this.flushHandler = flushHandler;

        periodicFlushHandler.scheduleWithFixedDelay(() -> {
            synchronized (this) {
                var toFlush = copyContentsAndClear();
                if (toFlush.isEmpty()) {
                    return;
                }
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

        FlushData flushData = null;
        synchronized (this) {
            if (totalSize >= bufferSize) {
                flushData = copyContentsAndClear();
            }
        }

        if (flushData != null && !flushData.isEmpty()) {
            FlushData finalFlushData = flushData;
            flushExecutor.execute(() -> flushAll(finalFlushData));
        }
    }

    private void flushAll(FlushData toFlush) {
        //order is important
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
    public void forceFlush() {
        // Flush any remaining data in the buffer
        FlushData finalFlushData;
        synchronized (this) {
            finalFlushData = copyContentsAndClear();
        }
        // Submit final flush task if there's data
        if (!finalFlushData.isEmpty()) {
            flushExecutor.submit(() -> flushAll(finalFlushData));
        }
        flushExecutor.shutdown();
        periodicFlushHandler.shutdown();
        // Wait for all tasks to complete with timeout
        long startTime = System.currentTimeMillis();
        long currentTime = startTime;
        while (currentTime - startTime < this.flushTimeout || (flushExecutor.isShutdown() && periodicFlushHandler.isShutdown())) {
            currentTime = System.currentTimeMillis();
            try {
                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Flush interrupted");
                break;
            }
        }
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

        public boolean isEmpty() {
            return blocks.isEmpty()
                   && logs.isEmpty()
                   && blockEntered.isEmpty()
                   && blockExited.isEmpty()
                   && blockReturned.isEmpty();
        }
    }
}