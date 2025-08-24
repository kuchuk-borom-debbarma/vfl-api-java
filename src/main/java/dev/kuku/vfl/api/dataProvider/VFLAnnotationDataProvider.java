package dev.kuku.vfl.api.dataProvider;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.dataProvider.VFLDataProvider;
import dev.kuku.vfl.internal.util.FlowUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Stack;

public class VFLAnnotationDataProvider implements VFLDataProvider {
    public static final ThreadLocal<@Nullable Stack<BlockContext>> CONTEXT = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(VFLAnnotationDataProvider.class);
    private final VFLBuffer buffer;

    public VFLAnnotationDataProvider(VFLBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public @Nullable BlockContext getBlockContext() {
        Stack<BlockContext> stack = CONTEXT.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    @Override
    public @Nullable VFLBuffer getVFLBuffer() {
        return this.buffer;
    }

    public void pushBlockContextToThreadLocalStack(BlockContext blockContext) {
        Stack<BlockContext> stack = CONTEXT.get();
        if (stack == null) {
            log.debug("pushBlockContextToThreadLocalStack: stack is null. Creating a new stack");
            CONTEXT.set(new Stack<>());
        }
        stack = CONTEXT.get();
        if(stack == null) {
            log.error("Failed to create a new stack for thread-local context");
            return;
        }
        stack.push(blockContext);
    }

    public void popLatestBlockContextFromThreadLocalStack() {
        var stack = CONTEXT.get();
        if (stack == null || stack.isEmpty()) {
            log.warn("No sub-block context found in thread-local stack");
            return;
        }
        var currentBlockContext = stack.pop();
        log.debug("Popped latest sub block context ${currentBlockContext.getBlock().getName()}-${currentBlockContext.getBlock().getId()}");
        //method is exiting
        FlowUtil.PushBlockExitedToBuffer(currentBlockContext.getBlock().getId());
        //method returned to caller
        FlowUtil.PushBlockReturnedToBuffer(currentBlockContext.getBlock().getId());

        //Cleanup thread local variable
        if (stack.isEmpty()) {
            log.debug("Thread-local context stack is now empty for thread ${Thread.currentThread().getName()}, removing it");
            CONTEXT.remove();
        }
    }
}
