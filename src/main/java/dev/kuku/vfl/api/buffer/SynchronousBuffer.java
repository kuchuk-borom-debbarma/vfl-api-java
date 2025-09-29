package dev.kuku.vfl.api.buffer;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.dto.BlockUpdate;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

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
    private final Map<String, BlockUpdate> blockUpdates;

    public SynchronousBuffer(VFLFlushHandler flushHandler, int flushSize) {
        this.flushHandler = flushHandler;
        this.flushSize = flushSize;

        // Initialize with reasonable capacity
        int capacity = Math.max(16, flushSize / 4);
        this.logs = new ArrayList<>(capacity);
        this.blocks = new ArrayList<>(capacity);
        this.blockUpdates = new HashMap<>(capacity);
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
    public void pushBlockReturned(String blockId, long time) {
        addItem(() -> {
            if (blockUpdates.containsKey(blockId)) {
                var current = blockUpdates.get(blockId);
                current.setReturnedAt(time);
            } else {
                blockUpdates.put(blockId, new BlockUpdate(null, null, time, null, null));
            }
        });
    }

    @Override
    public void pushBlockEntered(String blockId, long time) {
        addItem(() -> {
            if (blockUpdates.containsKey(blockId)) {
                var current = blockUpdates.get(blockId);
                current.setEnteredAt(time);
            } else {
                blockUpdates.put(blockId, new BlockUpdate(time, null, null, null, null));
            }
        });
    }

    @Override
    public void pushBlockExited(String blockId, long time) {
        addItem(() -> {
            if (blockUpdates.containsKey(blockId)) {
                var current = blockUpdates.get(blockId);
                current.setExitedAt(time);
            } else {
                blockUpdates.put(blockId, new BlockUpdate(null, time, null, null, null));
            }
        });
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
                    new HashMap<>(blockUpdates)
            );

            // Clear all collections
            logs.clear();
            blocks.clear();
            blockUpdates.clear();
            totalSize.set(0);

            return data;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Inner class to encapsulate flush data and operations
    private record FlushData(List<BlockLog> logs, List<Block> blocks, Map<String, BlockUpdate> blockUpdates) {

        private static FlushData empty() {
            return new FlushData(List.of(), List.of(), Map.of());
        }

        private boolean isEmpty() {
            return logs.isEmpty() && blocks.isEmpty() &&
                   blockUpdates.isEmpty();
        }

        private void flushUsing(VFLFlushHandler flushHandler) {
            if (!blocks.isEmpty()) flushHandler.flushBlocks(blocks);
            if (!logs.isEmpty()) flushHandler.flushLogs(logs);
            if (!blockUpdates.isEmpty()) flushHandler.flushBlockUpdates(blockUpdates);
        }
    }
}