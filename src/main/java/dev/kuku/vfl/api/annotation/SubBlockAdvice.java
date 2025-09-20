package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeTraceBlock;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Stack;

/**
 * Internal ByteBuddy advice class injected into methods annotated with {@link dev.kuku.vfl.api.annotation.SubBlock}.
 */
public final class SubBlockAdvice {
    public static final Logger log = LoggerFactory.getLogger(SubBlockAdvice.class);
    public static SubBlockAdvice instance = new SubBlockAdvice();

    private SubBlockAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onSubBlockEntered(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        instance.methodEntered(method, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onSubBlockExited(@Advice.Origin Method method, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
        instance.methodExited(method, args, throwable);
    }

    public void methodEntered(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        //Validation
        Stack<BlockContext> threadContextStack = VFLAnnotation.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("Sub block method called without parent block context!");
            return;
        }
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            log.error("Sub block method called but buffer is null!");
            return;
        }
        BlockContext parentContext = threadContextStack.peek();
        if (parentContext == null) {
            log.error("Sub block method called but parent context is null!");
            return;
        }
        //Create sub block for the method, createdAt will be set by constructor
        Block subBlock = new Block(method.getName(), parentContext.getBlock().getId());
        long time = Instant.now().toEpochMilli();
        buffer.pushBlock(subBlock);
        buffer.pushBlockEntered(subBlock.getId(), time);
        //Create sub block start log for current block's context
        BlockLog subBlockStartLog = new BlockLog(null,
                parentContext.getBlock().getId(),
                parentContext.getCurrentLogId(),
                subBlock.getId(),
                LogTypeTraceBlock.TRACE_PRIMARY);
        buffer.pushLog(subBlockStartLog);
        //Push the new block context onto the stack, since now this method is the one being invoked so all logs will be for this block context
        threadContextStack.push(new BlockContext(subBlock));
        //Set the sub block start log as the next step of the current block
        parentContext.setCurrentLogId(subBlockStartLog.getId());
    }

    public void methodExited(@Advice.Origin Method method, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
        //Validation
        Stack<BlockContext> threadContextStack = VFLAnnotation.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("Sub block method exited without parent block context!");
            return;
        }
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            log.warn("No VFL Annotation buffer");
            return;
        }
        BlockContext subBlockContext = VFLAnnotation.Util.popLatestContext(true);
        if (subBlockContext == null) {
            log.error("Sub block method popped but it's null");
            return;
        }
        //If exception was thrown, log it
        if (throwable != null) {
            BlockLog errorLog = new BlockLog("Exception : " + throwable.getMessage(),
                    subBlockContext.getBlock().getId(), subBlockContext.getCurrentLogId(), LogTypeBase.ERROR);
            buffer.pushLog(errorLog);
        }
        long time = Instant.now().toEpochMilli();
        buffer.pushBlockExited(subBlockContext.getBlock().getId(), time);
        buffer.pushBlockReturned(subBlockContext.getBlock().getId(), time);

    }
}