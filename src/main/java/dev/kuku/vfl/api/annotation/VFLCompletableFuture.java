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
import java.util.function.Supplier;

public class VFLCompletableFuture {
    private static final Logger log = LoggerFactory.getLogger(VFLCompletableFuture.class);

    // Existing runAsync methods
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

    // New supplyAsync methods
    public static <T> CompletableFuture<T> supplyAsync(String blockName, String message, Supplier<T> supplier) {
        return supplyAsync(blockName, message, supplier, null);
    }

    public static <T> CompletableFuture<T> supplyAsync(String blockName, String message, Supplier<T> supplier, Executor executor) {
        //1. Copy current thread's block Context, skip if it's null
        var ctxStack = VFLAnnotation.threadContextStack.get();
        if (ctxStack == null || ctxStack.isEmpty()) {
            log.debug("ctxStack is null or empty. Skipping VFLCompletable future wrapping");
            return executor != null ?
                    CompletableFuture.supplyAsync(supplier, executor) :
                    CompletableFuture.supplyAsync(supplier);
        }

        var ctx = ctxStack.peek();
        if (ctx == null) {
            log.debug("ctx is null. Skipping VFLCompletable future wrapping");
            return executor != null ?
                    CompletableFuture.supplyAsync(supplier, executor) :
                    CompletableFuture.supplyAsync(supplier);
        }

        var ctxCopy = new BlockContext(ctx);

        //2. Create a supplier wrapper which does a lot of things before invoking the actual supplier
        Supplier<T> updatedSupplier = () -> {
            Block asyncBlock;
            Stack<BlockContext> executorCtxStack = null;
            T result;

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

                // Execute the actual supplier
                result = supplier.get();

                // Log successful result
                if (VFLAnnotation.buffer != null && !executorCtxStack.isEmpty()) {
                    var currentContext = executorCtxStack.peek();
                    if (currentContext != null) {
                        BlockLog resultLog = new BlockLog(
                                "Async operation completed successfully" + (result != null ? ": " + result : ""),
                                currentContext.getBlock().getId(),
                                currentContext.getCurrentLogId(),
                                LogTypeBase.INFO
                        );
                        VFLAnnotation.buffer.pushLog(resultLog);
                    }
                }

                return result;

            } catch (Exception e) {
                //Log exception and rethrow
                log.error("Exception in async supply block: {}", e.getMessage(), e);
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
                CompletableFuture.supplyAsync(updatedSupplier, executor) :
                CompletableFuture.supplyAsync(updatedSupplier);
    }

    // Utility methods for creating already completed futures with VFL context
    public static <T> CompletableFuture<T> completedFuture(String blockName, String message, T value) {
        var ctxStack = VFLAnnotation.threadContextStack.get();
        if (ctxStack == null || ctxStack.isEmpty()) {
            log.debug("ctxStack is null or empty. Returning standard completedFuture");
            return CompletableFuture.completedFuture(value);
        }

        var ctx = ctxStack.peek();
        if (ctx == null) {
            log.debug("ctx is null. Returning standard completedFuture");
            return CompletableFuture.completedFuture(value);
        }

        try {
            // Create a quick block to represent this completed future
            var completedBlock = new Block(blockName, ctx.getBlock().getId());

            var completedLog = new BlockLog(message + (value != null ? ": " + value : ""),
                    ctx.getBlock().getId(),
                    ctx.getCurrentLogId(),
                    completedBlock.getId(),
                    LogTypeTraceBlock.TRACE_PARALLEL
            );

            if (VFLAnnotation.buffer != null) {
                VFLAnnotation.buffer.pushBlock(completedBlock);
                VFLAnnotation.buffer.pushLog(completedLog);
                VFLAnnotation.buffer.pushBlockEntered(completedBlock.getId());
                VFLAnnotation.buffer.pushBlockExited(completedBlock.getId());
                VFLAnnotation.buffer.pushBlockReturned(completedBlock.getId());
            }
        } catch (Exception e) {
            log.warn("Error logging completed future: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(value);
    }

    public static CompletableFuture<Void> completedFuture(String blockName, String message) {
        return completedFuture(blockName, message, null);
    }
}