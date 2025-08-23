package dev.kuku.vfl.internal;

import dev.kuku.vfl.internal.models.BlockContext;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.util.FlowUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class VFL {
    private final static Logger log = LoggerFactory.getLogger(VFL.class);

    @Nullable
    abstract BlockContext getBlockContext();

    private void logInternal(String message, LogTypeBase type) {
        BlockContext ctx = getBlockContext();
        if (ctx == null) {
            log.error("No block context found for log message: {}", message);
            return;
        }
        BlockLog createdLog = FlowUtil.CreateLogAndPushToBuffer(message, type);
        ctx.setCurrentLogId(createdLog.getId());
    }

    void info(String message, Object... args) {
        //TODO create message with args
        logInternal(message, LogTypeBase.INFO);
    }
}
