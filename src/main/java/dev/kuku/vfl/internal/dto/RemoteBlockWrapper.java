package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

public class RemoteBlockWrapper {
    public Block remoteBlock;

    public RemoteBlockWrapper(Block remoteBlock) {
        this.remoteBlock = remoteBlock;
    }

    public RemoteBlockWrapper() {
    }

    public Block getRemoteBlock() {
        return remoteBlock;
    }

    public void setRemoteBlock(Block remoteBlock) {
        this.remoteBlock = remoteBlock;
    }
}
