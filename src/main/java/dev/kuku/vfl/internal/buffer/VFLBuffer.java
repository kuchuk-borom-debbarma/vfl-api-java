package dev.kuku.vfl.internal.buffer;

import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

public interface VFLBuffer {
    void pushLog(BlockLog log);

    void pushBlock(Block block);

    void pushBlockReturned(String blockId);

    void pushBlockEntered(String blockId);

    void pushBlockExited(String blockId);

    void forceFlush();
}
