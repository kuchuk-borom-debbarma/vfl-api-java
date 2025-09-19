package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeTraceBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VFLCompletableFuture {
    private static final Logger log = LoggerFactory.getLogger(VFLCompletableFuture.class);

    public static CompletableFuture<Void> runAsync(String blockName, String message, Runnable runnable) {
        return runAsync(blockName, message, runnable, null);
    }

    public static CompletableFuture<Void> runAsync(String blockName, String message, Runnable runnable, Executor executor) {
        //1. Copy current thread's block Context, skip if it's null
        var ctxStack = VFLAnnotation.threadContextStack.get();
        if (ctxStack == null || ctxStack.isEmpty()) {
            log.debug("ctxStack is null or empty. Skipping VFLCompletable future wrapping");
            return executor != null ?
                    CompletableFuture.runAsync(runnable, executor) :
                    CompletableFuture.runAsync(runnable);
        }

        var ctx = ctxStack.peek();
        if (ctx == null) {
            log.debug("ctx is null. Skipping VFLCompletable future wrapping");
            return executor != null ?
                    CompletableFuture.runAsync(runnable, executor) :
                    CompletableFuture.runAsync(runnable);
        }

        var ctxCopy = new BlockContext(ctx);

        //2. Create a runnable wrapper which does a lot of things before invoking the actual runnable
        Runnable updatedRunnable = () -> {
            Block asyncBlock;
            Stack<BlockContext> executorCtxStack = null;

            try {
                //3. Create sub block to represent this async operation
                asyncBlock = new Block(blockName, ctxCopy.getBlock().getId());

                //4. Create async sub block start log for parent log (ctxCopy) to show the start of a parallel block starting
                var asyncFireForgetBlockStartLog = new BlockLog(message,
                        ctxCopy.getBlock().getId(),
                        ctxCopy.getCurrentLogId(),
                        asyncBlock.getId(),
                        LogTypeTraceBlock.TRACE_PARALLEL
                );

                //5. Push the block and log to buffer
                if (VFLAnnotation.buffer != null) {
                    VFLAnnotation.buffer.pushBlock(asyncBlock);
                    VFLAnnotation.buffer.pushLog(asyncFireForgetBlockStartLog);
                    VFLAnnotation.buffer.pushBlockEntered(asyncBlock.getId());
                }

                //6. Create blockContext for asyncBlock and push it to executor's context stack
                executorCtxStack = VFLAnnotation.threadContextStack.get();
                if (executorCtxStack == null) {
                    log.debug("Executor's ctxStack is empty! creating new stack");
                    executorCtxStack = new Stack<>();
                    VFLAnnotation.threadContextStack.set(executorCtxStack);
                }
                executorCtxStack.push(new BlockContext(asyncBlock));
                // Execute the actual runnable
                runnable.run();

            } catch (Exception e) {
                //Log exception and rethrow
                log.error("Exception in async block: {}", e.getMessage(), e);
                // VFL error if we have context
                if (executorCtxStack != null && !executorCtxStack.isEmpty()) {
                    var currentContext = executorCtxStack.peek();
                    if (currentContext != null && VFLAnnotation.buffer != null) {
                        BlockLog errorLog = new BlockLog(
                                "Exception: " + e.getMessage(),
                                currentContext.getBlock().getId(),
                                currentContext.getCurrentLogId(),
                                LogTypeBase.ERROR
                        );
                        VFLAnnotation.buffer.pushLog(errorLog);
                    }
                }
                throw e;
            } finally {
                //clean up stack
                var poppedCt = VFLAnnotation.Util.popLatestContext(true);
                if (poppedCt == null) {
                    log.error("poppedCt is null");
                } else {
                    // Complete block lifecycle
                    VFLAnnotation.buffer.pushBlockExited(poppedCt.getBlock().getId());
                    VFLAnnotation.buffer.pushBlockReturned(poppedCt.getBlock().getId());
                }
            }
        };

        return executor != null ?
                CompletableFuture.runAsync(updatedRunnable, executor) :
                CompletableFuture.runAsync(updatedRunnable);
    }
}