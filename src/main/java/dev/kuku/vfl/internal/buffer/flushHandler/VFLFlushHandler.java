package dev.kuku.vfl.internal.buffer.flushHandler;

import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

import java.util.List;
import java.util.Map;

public interface VFLFlushHandler {
    void flushLogs(List<BlockLog> logs);

    void flushBlocks(List<Block> blocks);

    void flushBlockEntered(Map<String, Long> blockIds);

    void flushBlockExited(Map<String, Long> blockIds);

    void flushBlockReturned(Map<String, Long> blockIds);

}
