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

    // Use read-write lock for better read performance
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    // Atomic counter for fast size checking without acquiring locks
    private final AtomicInteger totalSize = new AtomicInteger(0);

    // Will be initialized in constructor with appropriate capacity
    private final List<BlockLog> logsToFlush;
    private final List<Block> blocksToFlush;
    private final Map<String, Long> blockEnteredToFlush;
    private final Map<String, Long> blockExitedToFlush;
    private final Map<String, Long> blockReturnedToFlush;

    public SynchronousBuffer(VFLFlushHandler flushHandler, int flushSize) {
        this.flushHandler = flushHandler;
        this.flushSize = flushSize;

        // Initialize collections with reasonable capacity to minimize resizing
        int initialCapacity = Math.max(16, flushSize / 4);
        this.logsToFlush = new ArrayList<>(initialCapacity);
        this.blocksToFlush = new ArrayList<>(initialCapacity);
        this.blockEnteredToFlush = new HashMap<>(initialCapacity);
        this.blockExitedToFlush = new HashMap<>(initialCapacity);
        this.blockReturnedToFlush = new HashMap<>(initialCapacity);
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
        checkAndFlush();
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
        checkAndFlush();
    }

    @Override
    public void pushBlockReturned(String blockId) {
        long timestamp = System.currentTimeMillis();
        writeLock.lock();
        try {
            blockReturnedToFlush.put(blockId, timestamp);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlush();
    }

    @Override
    public void pushBlockEntered(String blockId) {
        long timestamp = System.currentTimeMillis();
        writeLock.lock();
        try {
            blockEnteredToFlush.put(blockId, timestamp);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlush();
    }

    @Override
    public void pushBlockExited(String blockId) {
        long timestamp = System.currentTimeMillis();
        writeLock.lock();
        try {
            blockExitedToFlush.put(blockId, timestamp);
            totalSize.incrementAndGet();
        } finally {
            writeLock.unlock();
        }
        checkAndFlush();
    }

    @Override
    public void flush() {
        // Create local variables to hold the data to flush
        List<Block> blocksSnapshot;
        List<BlockLog> logsSnapshot;
        Map<String, Long> blockEnteredSnapshot;
        Map<String, Long> blockExitedSnapshot;
        Map<String, Long> blockReturnedSnapshot;

        writeLock.lock();
        try {
            // Fast exit if nothing to flush
            if (totalSize.get() == 0) {
                return;
            }

            // Create snapshots and clear originals atomically
            blocksSnapshot = new ArrayList<>(this.blocksToFlush);
            logsSnapshot = new ArrayList<>(this.logsToFlush);
            blockEnteredSnapshot = new HashMap<>(this.blockEnteredToFlush);
            blockExitedSnapshot = new HashMap<>(this.blockExitedToFlush);
            blockReturnedSnapshot = new HashMap<>(this.blockReturnedToFlush);

            // Clear all collections
            this.blocksToFlush.clear();
            this.logsToFlush.clear();
            this.blockEnteredToFlush.clear();
            this.blockExitedToFlush.clear();
            this.blockReturnedToFlush.clear();

            // Reset counter
            totalSize.set(0);
        } finally {
            writeLock.unlock();
        }

        // Perform flush operations outside of lock to minimize lock contention
        if (!blocksSnapshot.isEmpty()) {
            flushHandler.flushBlocks(blocksSnapshot);
        }
        if (!logsSnapshot.isEmpty()) {
            flushHandler.flushLogs(logsSnapshot);
        }
        if (!blockEnteredSnapshot.isEmpty()) {
            flushHandler.flushBlockEntered(blockEnteredSnapshot);
        }
        if (!blockExitedSnapshot.isEmpty()) {
            flushHandler.flushBlockExited(blockExitedSnapshot);
        }
        if (!blockReturnedSnapshot.isEmpty()) {
            flushHandler.flushBlockReturned(blockReturnedSnapshot);
        }
    }

    private void checkAndFlush() {
        // Fast check without acquiring lock
        if (totalSize.get() >= flushSize) {
            flush();
        }
    }

    private long getCurrentTime() {
        return Instant.now().toEpochMilli();
    }

    public int getCurrentSize() {
        return totalSize.get();
    }

    public boolean isEmpty() {
        return totalSize.get() == 0;
    }
}