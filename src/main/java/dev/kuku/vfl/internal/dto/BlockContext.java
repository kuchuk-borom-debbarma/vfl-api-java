package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockContext {
    private final Block block;
    private final AtomicBoolean firstLogPushed = new AtomicBoolean(false);

    public void setCurrentLogId(String currentLogId) {
        this.currentLogId = currentLogId;
    }

    public String getCurrentLogId() {
        return currentLogId;
    }

    public AtomicBoolean getFirstLogPushed() {
        return firstLogPushed;
    }

    public Block getBlock() {
        return block;
    }


    private String currentLogId = null;

    public BlockContext(Block block) {
        this.block = block;
    }
}
