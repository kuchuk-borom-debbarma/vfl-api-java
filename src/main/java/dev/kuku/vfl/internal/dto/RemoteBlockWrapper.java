package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

public class RemoteBlockWrapper {
    public final Block remoteBlock;

    public RemoteBlockWrapper(Block remoteBlock) {
        this.remoteBlock = remoteBlock;
    }
}
