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
        BlockContext parentBlockContext = threadContextStack.peek();
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
        //Create sub block for the method
        Block subBlock = new Block(method.getName(), parentBlockContext.getBlock().getId());
        buffer.pushBlock(subBlock);
        //Create sub block start log for current block's context
        BlockLog subBlockStartLog = new BlockLog(null, parentBlockContext.getCurrentLogId(), subBlock.getId(), LogTypeTraceBlock.LINEAR_TRACE);
        buffer.pushLog(subBlockStartLog);
        //Set the sub block start log as the next step of the current block
        parentBlockContext.setCurrentLogId(subBlockStartLog.getId());
        //Push the new block context onto the stack, since now this method is the one being invoked so all logs will be for this block context
        threadContextStack.push(new BlockContext(subBlock));
        //Notify buffer that we have entered a new block.
        buffer.pushBlockStarted(subBlock.getId());
    }

    public void methodExited(@Advice.Origin Method method, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
        //Validation
        Stack<BlockContext> threadContextStack = VFLAnnotation.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("Sub block method exited without parent block context!");
            return;
        }
        //Pop the current block context since we are exiting the method
        BlockContext subBlockContext = threadContextStack.pop();
        //If exception was thrown, log it
        if (throwable != null) {
            VFLBuffer buffer = VFLAnnotation.buffer;
            if (buffer == null) {
                log.error("Sub block method exited but buffer is null!");
                return;
            }
            BlockLog errorLog = new BlockLog("Exception : ${throwable.getMessage()}", subBlockContext.getCurrentLogId(), LogTypeBase.ERROR);
            buffer.pushLog(errorLog);
            //Set the error log as the next step of the current block
            subBlockContext.setCurrentLogId(errorLog.getId());
            buffer.pushBlockFinished(subBlockContext.getBlock().getId());
            buffer.pushBlockReturned(subBlockContext.getBlock().getId());
        } else {
            VFLBuffer buffer = VFLAnnotation.buffer;
            if (buffer == null) {
                log.error("Sub block method exited but buffer is null!");
                return;
            }
            buffer.pushBlockFinished(subBlockContext.getBlock().getId());
            buffer.pushBlockReturned(subBlockContext.getBlock().getId());
        }
    }
}
