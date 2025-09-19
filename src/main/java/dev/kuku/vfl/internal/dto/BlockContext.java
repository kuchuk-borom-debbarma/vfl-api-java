package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

public class BlockContext {
    private final Block block;
    private String currentLogId = null;

    public void setCurrentLogId(String currentLogId) {
        this.currentLogId = currentLogId;
    }

    public String getCurrentLogId() {
        return currentLogId;
    }


    public Block getBlock() {
        return block;
    }


    public BlockContext(Block block) {
        this.block = block;
    }

    public BlockContext(BlockContext existingContext) {
        this.block = existingContext.block;
        this.currentLogId = existingContext.currentLogId;
    }

    @Override
    public String toString() {
        return "BlockContext{" +
               "block=" + block +
               ", currentLogId='" + currentLogId + '\'' +
               '}';
    }
}
