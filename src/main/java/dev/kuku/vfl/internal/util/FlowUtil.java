package dev.kuku.vfl.internal.util;

import dev.kuku.vfl.api.VFLInitializer;
import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeReferencedBlock;
import dev.kuku.vfl.internal.dataProvider.VFLDataProvider;
import io.github.robsonkades.uuidv7.UUIDv7;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowUtil {

    private static final Logger log = LoggerFactory.getLogger(FlowUtil.class);

    /**
     * Create log for current context and push to buffer
     *
     * @param message message of the log
     * @param type
     * @return
     */
    public static @Nullable BlockLog CreateLogForContextAndPush2Buffer(String message, LogTypeBase type) {
        VFLDataProvider provider = VFLInitializer.DATA_PROVIDER;
        if (provider == null) {
            FlowUtil.log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return null;
        }
        VFLBuffer buffer = provider.getVFLBuffer();
        BlockContext context = provider.getBlockContext();
        if (buffer == null || context == null) {
            FlowUtil.log.error("VFLBuffer is null! Cannot push log to buffer.");
            return null;
        }
        if (context == null) {
            log.error("VFLBlockContext is null! Cannot push log to buffer.");
            return null;
        }
        BlockLog log = new BlockLog(message, context.getCurrentLogId(), type);
        buffer.pushLog(log);
        return log;
    }

    public static  @Nullable BlockLog CreateLogForContextAndPush2Buffer(String message, String referencedBlockId, LogTypeReferencedBlock type) {
        VFLDataProvider provider = VFLInitializer.DATA_PROVIDER;
        if (provider == null) {
            FlowUtil.log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return null;
        }
        VFLBuffer buffer = provider.getVFLBuffer();
        if (buffer == null) {
            FlowUtil.log.error("VFLBuffer is null! Cannot push log to buffer.");
            return null;
        }
        BlockContext context = provider.getBlockContext();
        if (context == null) {
            log.error("VFLBlockContext is null! Cannot push log to buffer.");
            return null;
        }
        BlockLog log = new BlockLog(message, context.getCurrentLogId(), referencedBlockId, type);
        buffer.pushLog(log);
        return log;
    }

    public static @Nullable Block CreateBlockAndPushToBuffer(String name) {
        VFLDataProvider provider = VFLInitializer.DATA_PROVIDER;
        if (provider == null) {
            FlowUtil.log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return null;
        }
        VFLBuffer buffer = provider.getVFLBuffer();
        if (buffer == null) {
            FlowUtil.log.error("VFLBuffer is null! Cannot push log to buffer.");
            return null;
        }
        Block block = new Block(UUIDv7.randomUUID().toString(), name);
        buffer.pushBlock(block);
        return block;
    }

    public static void PushBlockEnteredToBuffer(String blockId) {
        VFLDataProvider provider = VFLInitializer.DATA_PROVIDER;
        if (provider == null) {
            FlowUtil.log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return;
        }
        VFLBuffer buffer = provider.getVFLBuffer();
        if (buffer == null) {
            FlowUtil.log.error("VFLBuffer is null! Cannot push log to buffer.");
            return;
        }
        buffer.pushBlockEntered(blockId);
    }

    public static void PushBlockReturnedToBuffer(String blockId) {
        VFLDataProvider provider = VFLInitializer.DATA_PROVIDER;
        if (provider == null) {
            FlowUtil.log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return;
        }
        VFLBuffer buffer = provider.getVFLBuffer();
        if (buffer == null) {
            FlowUtil.log.error("VFLBuffer is null! Cannot push log to buffer.");
            return;
        }
        buffer.pushBlockReturned(blockId);
    }

    public static void PushBlockExitedToBuffer(String blockId) {
        VFLDataProvider provider = VFLInitializer.DATA_PROVIDER;
        if (provider == null) {
            FlowUtil.log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return;
        }
        VFLBuffer buffer = provider.getVFLBuffer();
        if (buffer == null) {
            FlowUtil.log.error("VFLBuffer is null! Cannot push log to buffer.");
            return;
        }
        buffer.pushBlockExited(blockId);

    }
}
