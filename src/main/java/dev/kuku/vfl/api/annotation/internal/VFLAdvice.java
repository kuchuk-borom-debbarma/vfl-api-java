package dev.kuku.vfl.api.annotation.internal;

import dev.kuku.vfl.api.annotation.AnnotationData;
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
public final class VFLAdvice {
    public static final Logger log = LoggerFactory.getLogger(VFLAdvice.class);

    private VFLAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onSubBlockEntered(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        //Validation
        AnnotationData annotationData = AnnotationData.instance;
        if (annotationData == null) {
            log.error("AnnotationData has not been initialized!");
            return;
        }
        Stack<BlockContext> threadContextStack = annotationData.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("Sub block method called without parent block context!");
            return;
        }
        BlockContext parentBlockContext = threadContextStack.peek();
        VFLBuffer buffer = annotationData.buffer;

        //Create sub block for the method
        Block subBlock = new Block(method.getName());
        buffer.pushBlock(subBlock);
        //Create sub block start log for current block's context
        BlockLog subBlockStartLog = new BlockLog(null, parentBlockContext.getCurrentLogId(), subBlock.getId(), LogTypeTraceBlock.LINEAR_TRACE);
        buffer.pushLog(subBlockStartLog);
        //Set the sub block start log as the next step of the current block
        parentBlockContext.setCurrentLogId(subBlockStartLog.getId());
        //Push the new block context onto the stack, since now this method is the one being invoked so all logs will be for this block context
        threadContextStack.push(new BlockContext(subBlock));
        //Notify buffer that we have entered a new block.
        buffer.pushBlockEntered(subBlock.getId());
    }

    public static void onSubBlockExited(@Advice.Origin Method method, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
        //Validation
        AnnotationData annotationData = AnnotationData.instance;
        if (annotationData == null) {
            log.error("AnnotationData has not been initialized!");
            return;
        }
        Stack<BlockContext> threadContextStack = annotationData.threadContextStack.get();
        if (threadContextStack == null || threadContextStack.isEmpty()) {
            log.error("Sub block method exited without parent block context!");
            return;
        }
        //Pop the current block context since we are exiting the method
        BlockContext subBlockContext = threadContextStack.pop();
        //If exception was thrown, log it
        if (throwable != null) {
            BlockLog errorLog = new BlockLog("Exception : ${throwable.getMessage()}", subBlockContext.getCurrentLogId(), LogTypeBase.ERROR);
            annotationData.buffer.pushLog(errorLog);
            //Set the error log as the next step of the current block
            subBlockContext.setCurrentLogId(errorLog.getId());
        }
        //Notify buffer that we sub block has exited
        annotationData.buffer.pushBlockExited(subBlockContext.getBlock().getId());
        //Notify buffer that the block has returned to it's parent. This ALWAYS comes after exit.
        annotationData.buffer.pushBlockReturned(subBlockContext.getBlock().getId());
    }
}
