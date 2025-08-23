package dev.kuku.vfl.internal.models;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockContext {
    private final Block block;
    private final AtomicBoolean firstLogPushed = new AtomicBoolean(false);

    public void setCurrentLogId(@MonotonicNonNull String currentLogId) {
        this.currentLogId = currentLogId;
    }

    public @MonotonicNonNull String getCurrentLogId() {
        return currentLogId;
    }

    public AtomicBoolean getFirstLogPushed() {
        return firstLogPushed;
    }

    public Block getBlock() {
        return block;
    }

    @MonotonicNonNull
    private String currentLogId = null;

    public BlockContext(Block block) {
        this.block = block;
    }
}
