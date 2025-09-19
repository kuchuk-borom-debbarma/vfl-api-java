package dev.kuku.vfl.internal;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import org.slf4j.Logger;

import static dev.kuku.vfl.internal.util.CommonUtil.FormatMessage;

public abstract class VFLBase {
    private final Logger log = org.slf4j.LoggerFactory.getLogger(VFLBase.class);

    protected abstract BlockContext getBlockContext();

    protected
    abstract VFLBuffer getVFLBuffer();

    public void info(String message, Object... args) {
        BlockContext ctx = getBlockContext();
        if (ctx == null) {
            log.warn("Failed to log. No Active context found!");
            return;
        }
        VFLBuffer buffer = getVFLBuffer();
        if (buffer == null) {
            log.warn("Failed to log. Buffer is null!");
            return;
        }
        String msg = FormatMessage(message, args);
        BlockLog l = new BlockLog(msg, ctx.getBlock().getId(), ctx.getCurrentLogId(), LogTypeBase.INFO);
        log.debug("Created log $l");
        buffer.pushLog(l);
        ctx.setCurrentLogId(l.getId());
    }

    public void warn(String message, Object... args) {
        BlockContext ctx = getBlockContext();
        if (ctx == null) {
            log.warn("Failed to log. No Active context found!");
            return;
        }
        VFLBuffer buffer = getVFLBuffer();
        if (buffer == null) {
            log.warn("Failed to log. Buffer is null!");
            return;
        }
        String msg = FormatMessage(message, args);
        BlockLog logEntry = new BlockLog(msg, ctx.getBlock().getId(), ctx.getCurrentLogId(), LogTypeBase.WARN);
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
        if (buffer == null) {
            log.warn("Failed to log. Buffer is null!");
            return;
        }
        String msg = FormatMessage(message, args);
        BlockLog logEntry = new BlockLog(msg, ctx.getBlock().getId(), ctx.getCurrentLogId(), LogTypeBase.ERROR);
        buffer.pushLog(logEntry);
        ctx.setCurrentLogId(logEntry.getId());
    }
}
