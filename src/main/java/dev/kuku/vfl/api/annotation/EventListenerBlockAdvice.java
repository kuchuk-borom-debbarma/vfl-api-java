package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.dto.PublishContext;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeTraceBlock;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Stack;

public class EventListenerBlockAdvice {
    public static final EventListenerBlockAdvice instance = new EventListenerBlockAdvice();

    private EventListenerBlockAdvice() {
    }

    @Advice.OnMethodEnter
    public static void MethodEntered(@Advice.Origin Method origin, @Advice.AllArguments Object[] args) {
        instance.entered(origin, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void MethodExit(@Advice.Origin Method origin, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
        instance.exit(origin, args, throwable);
    }

    public void entered(Method origin, Object[] args) {
        Logger log = LoggerFactory.getLogger(origin.getDeclaringClass().getSimpleName() + "-" + origin.getName());
        log.info("Entered method: {} with args: {}", origin.getName(), args);
        //Validations
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            log.warn("VFLBuffer is not initialized. Skipping event listener block creation.");
            return;
        }
        // Attempt to find the publish context in the arguments
        PublishContext publishContext = null;
        for (Object arg : args) {
            if (arg instanceof PublishContext) {
                publishContext = (PublishContext) arg;
                log.info("Found PublishContext: {}", publishContext);
                break;
            }
        }
        if (publishContext == null) {
            log.warn("No PublishContext found in method arguments. Skipping event listener block creation.");
            return;
        }
        //Create listener block and push it
        Block eventListenerBlock = new Block(origin.getName(), publishContext.publishedBLock.getId());
        buffer.pushBlock(eventListenerBlock);
        //Create listener block log for publisher to link and push it
        BlockLog eventListenerLog = new BlockLog(null, publishContext.publishedBLock.getId(), null, eventListenerBlock.getId(), LogTypeTraceBlock.LISTEN_EVENT);
        buffer.pushLog(eventListenerLog);
        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null) {
            stack = new Stack<>();
            VFLAnnotation.threadContextStack.set(stack);
        }
        //Block entered
        buffer.pushBlockEntered(eventListenerBlock.getId());

        stack.push(new BlockContext(eventListenerBlock));
    }

    public void exit(Method method, Object[] args, Throwable throwable) {
        Logger log = LoggerFactory.getLogger(method.getDeclaringClass().getSimpleName() + "-" + method.getName());
        log.info("Exiting method: {} and throwable: {}", method.getName(),
                throwable != null ? throwable.getMessage() : "none");

        //Validations
        VFLBuffer buffer = VFLAnnotation.buffer;
        if (buffer == null) {
            log.warn("VFLBuffer is not initialized. Skipping event listener block completion.");
            return;
        }
        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null || stack.isEmpty()) {
            log.warn("VFL block stack is null or empty. Skipping event listener block completion.");
            return;
        }

        BlockContext currentContext = stack.pop();
        Block eventListenerBlock = currentContext.getBlock();

        if (throwable != null) {
            BlockLog errorLog = new BlockLog(throwable.getMessage(), currentContext.getBlock().getId(), currentContext.getCurrentLogId(), LogTypeBase.ERROR);
            buffer.pushLog(errorLog);
        }
        //Block finished and returned
        buffer.pushBlockExited(eventListenerBlock.getId());
        buffer.pushBlockReturned(eventListenerBlock.getId());
    }
}