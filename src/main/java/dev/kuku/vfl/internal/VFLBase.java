package dev.kuku.vfl.internal;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import static dev.kuku.vfl.internal.util.CommonUtil.FormatMessage;

public abstract class VFLBase {
    private final Logger log = org.slf4j.LoggerFactory.getLogger(VFLBase.class);

    protected abstract @Nullable BlockContext getBlockContext();

    protected
    abstract VFLBuffer getVFLBuffer();

    public void info(String message, Object... args) {
        BlockContext ctx = getBlockContext();
        if (ctx == null) {
            log.warn("Failed to log. No Active context found!");
            return;
        }
        VFLBuffer buffer = getVFLBuffer();
        String msg = FormatMessage(message, args);
        BlockLog log = new BlockLog(msg, ctx.getCurrentLogId(), LogTypeBase.INFO);
        buffer.pushLog(log);
        ctx.setCurrentLogId(log.getId());
    }

    public void warn(String message, Object... args) {
        BlockContext ctx = getBlockContext();
        if (ctx == null) {
            log.warn("Failed to log. No Active context found!");
            return;
        }
        VFLBuffer buffer = getVFLBuffer();
        String msg = FormatMessage(message, args);
        BlockLog logEntry = new BlockLog(msg, ctx.getCurrentLogId(), LogTypeBase.WARN);
        buffer.pushLog(logEntry);
        ctx.setCurrentLogId(logEntry.getId());
    }

    public void error(String message, Object... args) {
        BlockContext ctx = getBlockContext();
        if (ctx == null) {
            log.warn("Failed to log. No Active context found!");
            return;
        }
        VFLBuffer buffer = getVFLBuffer();
        String msg = FormatMessage(message, args);
        BlockLog logEntry = new BlockLog(msg, ctx.getCurrentLogId(), LogTypeBase.ERROR);
        buffer.pushLog(logEntry);
        ctx.setCurrentLogId(logEntry.getId());
    }
}
