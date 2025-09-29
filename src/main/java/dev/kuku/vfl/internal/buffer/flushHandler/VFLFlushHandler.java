package dev.kuku.vfl.internal.buffer.flushHandler;

import dev.kuku.vfl.internal.dto.BlockUpdate;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

import java.util.List;
import java.util.Map;

public interface VFLFlushHandler {
    void flushBlocks(List<Block> blocks);

    void flushLogs(List<BlockLog> logs);

    void flushBlockUpdates(Map<String, BlockUpdate> updates);
}
