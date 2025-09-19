package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.dto.RemoteBlockWrapper;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Stack;

public class RemoteBlockAdvice {
    //TODO remote block must act as root block with explicit warning (configurable) if it is missing context
    public static final RemoteBlockAdvice instance = new RemoteBlockAdvice();

    private RemoteBlockAdvice() {

    }

    @Advice.OnMethodEnter
    public static void MethodEntered(@Advice.Origin Method origin, @Advice.AllArguments Object[] args) {
        instance.entered(origin, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void MethodExit(@Advice.Origin Method origin, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
        instance.exited(origin, throwable);
    }


    public void entered(Method method, Object[] args) {
        Logger log = LoggerFactory.getLogger(method.getDeclaringClass().getSimpleName() + "-" + method.getName());
        log.info("[REMOTE BLOCK] Entered method: {} with args: {}", method.getName(), args);
        //Validations
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            log.warn("VFLBuffer is not initialized. Skipping remote block creation.");
            return;
        }

        // Attempt to find the publish context in the arguments
        RemoteBlockWrapper remoteBlockWrapper = null;
        for (Object arg : args) {
            if (arg instanceof RemoteBlockWrapper) {
                remoteBlockWrapper = (RemoteBlockWrapper) arg;
                log.info("Found RemoteBlockWrapper: {}", remoteBlockWrapper);
                break;
            }
        }
        if (remoteBlockWrapper == null) {
            log.error("Failed to find remote block wrapper in method arguments. Remote block cannot be created.");
            return;
        }
        Block remoteBlock = remoteBlockWrapper.remoteBlock;
        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null) {
            stack = new Stack<>();
            VFLAnnotation.threadContextStack.set(stack);
        }
        //Entered the block
        buffer.pushBlockEntered(remoteBlock.getId());
        stack.push(new BlockContext(remoteBlock));
    }

    public void exited(Method method, Throwable throwable) {
        Logger log = LoggerFactory.getLogger(method.getDeclaringClass().getSimpleName() + "-" + method.getName());
        log.info("[REMOTE BLOCK] Exited method: {} with throwable: {}", method.getName(),
                throwable != null ? throwable.getMessage() : "none");
        //Validations
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            log.warn("VFLBuffer is not initialized. Skipping remote block completion.");
            return;
        }
        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null || stack.isEmpty()) {
            log.warn("Thread context stack is empty. No remote block to complete.");
            return;
        }

        BlockContext context = stack.pop();
        if (throwable != null) {
            BlockLog errorLog = new BlockLog("Exception " + throwable.getMessage(), context.getBlock().getId(), context.getCurrentLogId(), LogTypeBase.ERROR);
            buffer.pushLog(errorLog);
            context.setCurrentLogId(errorLog.getId());
        }
        buffer.pushBlockExited(context.getBlock().getId());
    }
}