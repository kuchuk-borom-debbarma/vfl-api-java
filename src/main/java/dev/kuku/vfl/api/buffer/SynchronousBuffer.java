package dev.kuku.vfl.api.buffer;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SynchronousBuffer implements VFLBuffer {
    private final VFLFlushHandler flushHandler;
    private final int flushSize;

    // Simplified locking
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicInteger totalSize = new AtomicInteger(0);

    // Consolidated data structures
    private final List<BlockLog> logs;
    private final List<Block> blocks;
    private final Map<String, Long> blockEntered;
    private final Map<String, Long> blockExited;
    private final Map<String, Long> blockReturned;

    public SynchronousBuffer(VFLFlushHandler flushHandler, int flushSize) {
        this.flushHandler = flushHandler;
        this.flushSize = flushSize;

        // Initialize with reasonable capacity
        int capacity = Math.max(16, flushSize / 4);
        this.logs = new ArrayList<>(capacity);
        this.blocks = new ArrayList<>(capacity);
        this.blockEntered = new HashMap<>(capacity);
        this.blockExited = new HashMap<>(capacity);
        this.blockReturned = new HashMap<>(capacity);
    }

    @Override
    public void pushLog(BlockLog log) {
        addItem(() -> logs.add(log));
    }

    @Override
    public void pushBlock(Block block) {
        addItem(() -> blocks.add(block));
    }

    @Override
    public void pushBlockReturned(String blockId) {
        addItem(() -> blockReturned.put(blockId, getCurrentTime()));
    }

    @Override
    public void pushBlockEntered(String blockId) {
        addItem(() -> blockEntered.put(blockId, getCurrentTime()));
    }

    @Override
    public void pushBlockExited(String blockId) {
        addItem(() -> blockExited.put(blockId, getCurrentTime()));
    }

    @Override
    public void forceFlush() {
        FlushData data = extractDataForFlush();
        if (data.isEmpty()) {
            return;
        }

        // Flush outside of lock to minimize contention
        data.flushUsing(flushHandler);
    }

    public int getCurrentSize() {
        return totalSize.get();
    }

    public boolean isEmpty() {
        return totalSize.get() == 0;
    }

    // Private helper methods
    private void addItem(Runnable addOperation) {
        lock.writeLock().lock();
        try {
            addOperation.run();
            totalSize.incrementAndGet();
        } finally {
            lock.writeLock().unlock();
        }
        checkAndFlush();
    }

    private void checkAndFlush() {
        if (totalSize.get() >= flushSize) {
            forceFlush();
        }
    }

    private long getCurrentTime() {
        return Instant.now().toEpochMilli();
    }

    private FlushData extractDataForFlush() {
        lock.writeLock().lock();
        try {
            if (totalSize.get() == 0) {
                return FlushData.empty();
            }

            // Create snapshots and clear originals atomically
            FlushData data = new FlushData(
                    new ArrayList<>(logs),
                    new ArrayList<>(blocks),
                    new HashMap<>(blockEntered),
                    new HashMap<>(blockExited),
                    new HashMap<>(blockReturned)
            );

            // Clear all collections
            logs.clear();
            blocks.clear();
            blockEntered.clear();
            blockExited.clear();
            blockReturned.clear();
            totalSize.set(0);

            return data;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Inner class to encapsulate flush data and operations
    private static class FlushData {
        private final List<BlockLog> logs;
        private final List<Block> blocks;
        private final Map<String, Long> blockEntered;
        private final Map<String, Long> blockExited;
        private final Map<String, Long> blockReturned;

        private FlushData(List<BlockLog> logs, List<Block> blocks,
                          Map<String, Long> blockEntered, Map<String, Long> blockExited,
                          Map<String, Long> blockReturned) {
            this.logs = logs;
            this.blocks = blocks;
            this.blockEntered = blockEntered;
            this.blockExited = blockExited;
            this.blockReturned = blockReturned;
        }

        private static FlushData empty() {
            return new FlushData(List.of(), List.of(), Map.of(), Map.of(), Map.of());
        }

        private boolean isEmpty() {
            return logs.isEmpty() && blocks.isEmpty() &&
                   blockEntered.isEmpty() && blockExited.isEmpty() && blockReturned.isEmpty();
        }

        private void flushUsing(VFLFlushHandler flushHandler) {
            if (!logs.isEmpty()) flushHandler.flushLogs(logs);
            if (!blocks.isEmpty()) flushHandler.flushBlocks(blocks);
            if (!blockEntered.isEmpty()) flushHandler.flushBlockEntered(blockEntered);
            if (!blockExited.isEmpty()) flushHandler.flushBlockExited(blockExited);
            if (!blockReturned.isEmpty()) flushHandler.flushBlockReturned(blockReturned);
        }
    }
}