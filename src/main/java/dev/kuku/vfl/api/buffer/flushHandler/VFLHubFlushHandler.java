package dev.kuku.vfl.api.buffer.flushHandler;

import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;

import java.util.List;
import java.util.Map;

public class VFLHubFlushHandler implements VFLFlushHandler {
    @Override
    public void flushLogs(List<BlockLog> logs) {

    }

    @Override
    public void flushBlocks(List<Block> blocks) {

    }

    @Override
    public void flushBlockEntered(Map<String, Long> blockIds) {

    }

    @Override
    public void flushBlockExited(Map<String, Long> blockIds) {

    }

    @Override
    public void flushBlockReturned(Map<String, Long> blockIds) {

    }
}
