package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockContext {
    private final Block block;
    private final AtomicBoolean firstLogPushed = new AtomicBoolean(false);

    public void setCurrentLogId(@NonNull String currentLogId) {
        this.currentLogId = currentLogId;
    }
    public @Nullable String getCurrentLogId() {
        return currentLogId;
    }

    public AtomicBoolean getFirstLogPushed() {
        return firstLogPushed;
    }

    public Block getBlock() {
        return block;
    }


    private @MonotonicNonNull String currentLogId = null;

    public BlockContext(Block block) {
        this.block = block;
    }
}
