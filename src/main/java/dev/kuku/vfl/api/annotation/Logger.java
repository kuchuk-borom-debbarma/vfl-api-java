package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import org.slf4j.LoggerFactory;

import java.util.Stack;

import static dev.kuku.vfl.internal.util.CommonUtil.FormatMessage;

public class Logger {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Logger.class);

    public void info(String message, Object... args) {
        AnnotationData ad = AnnotationData.instance;
        if (ad == null) {
            log.error("AnnotationData not initialized! Setup ${VFLAnnotation.class.getSimpleName()} if before using VFL.");
        }
        Stack<BlockContext> threadContextStack = ad.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("No active block context found! Make sure to call this method within a VFL block.");
            return;
        }
        BlockContext currentContext = threadContextStack.peek();
        String msg = FormatMessage(message, args);
        BlockLog log = new BlockLog(msg, currentContext.getCurrentLogId(), LogTypeBase.INFO);
        ad.buffer.pushLog(log);
        currentContext.setCurrentLogId(log.getId());
    }

    public void warn(String message, Object... args) {
        AnnotationData ad = AnnotationData.instance;
        if (ad == null) {
            log.error("AnnotationData not initialized! Setup ${VFLAnnotation.class.getSimpleName()} if before using VFL.");
        }
        Stack<BlockContext> threadContextStack = ad.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("No active block context found! Make sure to call this method within a VFL block.");
            return;
        }
        BlockContext currentContext = threadContextStack.peek();
        String msg = FormatMessage(message, args);
        BlockLog logEntry = new BlockLog(msg, currentContext.getCurrentLogId(), LogTypeBase.WARN);
        ad.buffer.pushLog(logEntry);
        currentContext.setCurrentLogId(logEntry.getId());
    }

    public void error(String message, Object... args) {
        AnnotationData ad = AnnotationData.instance;
        if (ad == null) {
            log.error("AnnotationData not initialized! Setup ${VFLAnnotation.class.getSimpleName()} if before using VFL.");
        }
        Stack<BlockContext> threadContextStack = ad.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("No active block context found! Make sure to call this method within a VFL block.");
            return;
        }
        BlockContext currentContext = threadContextStack.peek();
        String msg = FormatMessage(message, args);
        BlockLog logEntry = new BlockLog(msg, currentContext.getCurrentLogId(), LogTypeBase.ERROR);
        ad.buffer.pushLog(logEntry);
        currentContext.setCurrentLogId(logEntry.getId());
    }
}
