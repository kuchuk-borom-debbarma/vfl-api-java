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
    private static EventListenerBlockAdvice instance = new EventListenerBlockAdvice();

    private EventListenerBlockAdvice() {
    }

    @Advice.OnMethodEnter
    public static void MethodEntered(@Advice.Origin Method origin, @Advice.AllArguments Object[] args) {
    }

    public static void MethodExit(@Advice.Origin Method origin, @Advice.AllArguments Object[] args, @Advice.Thrown Throwable throwable) {
    }

    public void entered(Method origin, Object[] args) {
        Logger log = LoggerFactory.getLogger("${origin.getClass().getSimpleName()}-${origin.getName()}");
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
        Block eventListenerBlock = new Block(origin.getName(), publishContext.publishedBLock.getId());
        buffer.pushBlock(eventListenerBlock);
        BlockLog eventListenerLog = new BlockLog(null, publishContext.publishedBLock.getId(), null, eventListenerBlock.getId(), LogTypeTraceBlock.LISTEN_EVENT);
        buffer.pushLog(eventListenerLog);

        Stack<BlockContext> stack = VFLAnnotation.threadContextStack.get();
        if (stack == null) {
            stack = new Stack<>();
            VFLAnnotation.threadContextStack.set(stack);
        }

        buffer.pushBlockEntered(eventListenerBlock.getId());

        stack.push(new BlockContext(eventListenerBlock));
    }

    public void exit(Method method, Object[] args, Throwable throwable) {
        Logger log = LoggerFactory.getLogger("${method.getClass().getSimpleName()}-${method.getName()}");
        log.info("Exiting method: {} with args: {} and throwable: {}", method.getName(), args, throwable);

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
