package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

public class BlockContext {
    private final Block block;

    public void setCurrentLogId(String currentLogId) {
        this.currentLogId = currentLogId;
    }

    public String getCurrentLogId() {
        return currentLogId;
    }


    public Block getBlock() {
        return block;
    }


    private String currentLogId = null;

    public BlockContext(Block block) {
        this.block = block;
    }

    @Override
    public String toString() {
        return "BlockContext{" +
               "block=" + block +
               ", currentLogId='" + currentLogId + '\'' +
               '}';
    }
}
