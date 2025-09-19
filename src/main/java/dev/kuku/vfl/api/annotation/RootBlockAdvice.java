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
    public static final RootBlockAdvice INSTANCE = new RootBlockAdvice();
    public static final Logger log = LoggerFactory.getLogger(RootBlockAdvice.class);

    private RootBlockAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        System.out.println("=== DEBUG: RootBlockAdvice.onEnter called for: " + method.getName() + " ===");
        System.out.flush();
        log.debug("Enter RootBlockAdvice.onEnter for method {}-{}",
                method.getDeclaringClass().getSimpleName(), method.getName());
        INSTANCE.methodEntered(method, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Origin Method method, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable threw) {
        System.out.println("=== DEBUG: RootBlockAdvice.onExit called for: " + method.getName() + " ===");
        System.out.flush();
        INSTANCE.methodExited(method, args, threw);
    }

    public void methodEntered(Method method, Object[] args) {
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass().getName() + "-" + method.getName());
        logger.info("Root block method entered: {}-{}", method.getName(), method.getDeclaringClass().getSimpleName());

        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null) {
            logger.debug("Stack is empty, creating new stack");
            VFLAnnotation.threadContextStack.set(new Stack<>());
            stack = VFLAnnotation.threadContextStack.get();
            logger.debug("Created stack = ${String.valueOf(stack)}");
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

        // Mark the block as entered
        buffer.pushBlockEntered(rootBlock.getId());

        stack.push(new BlockContext(rootBlock));
    }

    public void methodExited(Method method, Object[] args, Throwable throwable) {
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass().getName() + "-" + method.getName());
        logger.info("Exited root block method: {}-{}", method.getName(), method.getDeclaringClass().getSimpleName());

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
            logger.error("Root Method threw an exception: {}", throwable.getMessage(), throwable);
            BlockLog errorLog = new BlockLog("Exception : " + throwable.getMessage(),
                    blockContext.getBlock().getId(),
                    blockContext.getCurrentLogId(),
                    LogTypeBase.ERROR);
            blockContext.setCurrentLogId(errorLog.getId());
            buffer.pushLog(errorLog);
        }

        buffer.pushBlockExited(blockContext.getBlock().getId());
        buffer.pushBlockReturned(blockContext.getBlock().getId());
        buffer.forceFlush();
    }
}