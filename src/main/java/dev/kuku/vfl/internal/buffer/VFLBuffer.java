package dev.kuku.vfl.internal.buffer;

import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

public interface VFLBuffer {
    void pushLog(BlockLog log);

    void pushBlock(Block block);

    void pushBlockEntered(String blockId, long time);

    void pushBlockReturned(String blockId, long time);


    void pushBlockExited(String blockId, long time);

    void forceFlush();
}
