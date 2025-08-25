package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Stack;

public class RootBlockAdvice {
    private static final RootBlockAdvice INSTANCE = new RootBlockAdvice();

    private RootBlockAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        INSTANCE.methodEntered(method, args);
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.Origin Method method, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable threw) {
        INSTANCE.methodExisted(method, args, threw);
    }

    public void methodEntered(Method method, Object[] args) {
        Logger logger = LoggerFactory.getLogger("${method.getClass()}-${method.getName()}");
        logger.info("Root block method entered: ${method.getName()}-${method.getClass().getSimpleName()}");
        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null) {
            logger.debug("Stack is empty, creating new stack");
            VFLAnnotation.threadContextStack.set(new Stack<>());
            stack = VFLAnnotation.threadContextStack.get();
        }
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            logger.error("Buffer is null, cannot create root block");
            return;
        }
        if (stack == null) {
            logger.error("Stack is null, cannot create root block");
            return;
        }
        Block rootBlock = new Block(method.getName(), null);
        buffer.pushBlock(rootBlock);
        stack.push(new BlockContext(rootBlock));
    }

    public void methodExisted(Method method, Object[] args, Throwable throwable) {
        Logger logger = LoggerFactory.getLogger("${method.getClass()}-${method.getName()}");
        logger.info("Exited root block method: ${method.getName()}-${method.getClass().getSimpleName()}");
        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null || stack.isEmpty()) {
            logger.error("Stack is empty, cannot pop root block. Something went wrong!");
            return;
        }
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            logger.error("Buffer is null, cannot pop root block");
            return;
        }

        BlockContext blockContext = stack.pop();
        if (blockContext.getBlock().getParentBlockId() != null) {
            logger.error("Popped block is not a root block. Something went wrong!");
            return;
        }

        if (throwable != null) {
            logger.error("Root Method threw an exception: ${throwable.getMessage()}", throwable);
            BlockLog errorLog = new BlockLog("Exception : ${throwable.getMessage()}", blockContext.getBlock().getId(), blockContext.getCurrentLogId(), LogTypeBase.ERROR);
            blockContext.setCurrentLogId(errorLog.getId());
            buffer.pushLog(errorLog);
        }

        buffer.pushBlockExited(blockContext.getBlock().getId());
        buffer.pushBlockReturned(blockContext.getBlock().getId());
    }
}
