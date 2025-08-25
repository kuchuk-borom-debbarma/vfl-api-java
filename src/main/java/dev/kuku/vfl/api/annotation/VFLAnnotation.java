package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.VFLBase;
import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.dto.PublishContext;
import dev.kuku.vfl.internal.dto.RemoteBlockWrapper;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeTraceBlock;
import dev.kuku.vfl.internal.util.CommonUtil;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.Stack;
import java.util.function.Function;

public class VFLAnnotation extends VFLBase {
    static final ThreadLocal<@Nullable Stack<BlockContext>> threadContextStack = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(VFLAnnotation.class);
    static @Nullable VFLBuffer buffer = null;

    @Override
    protected @Nullable BlockContext getBlockContext() {
        final @Nullable Stack<BlockContext> stack = threadContextStack.get();
        if (stack == null) {
            log.warn("VFL block stack is null");
            return null;
        }
        if (stack.isEmpty()) {
            log.warn("VFL block stack is empty");
            return null;
        }
        return stack.peek();
    }

    @Override
    protected @Nullable VFLBuffer getVFLBuffer() {
        return VFLAnnotation.buffer;
    }

    public static synchronized void initialize(@Nullable VFLBuffer buffer) {
        VFLAnnotation.buffer = buffer;
        try {
            ByteBuddyInitializer.initializeAgent();
            log.info("[VFL] Instrumentation initialised successfully");
        } catch (Exception e) {
            log.error("[VFL] Initialisation failed", e);
            throw new RuntimeException(e);
        }
    }

    public static synchronized void initialize() {
        VFLAnnotation.initialize(null);
    }

    /**
     * Creates a publish event block and log, pushes them to buffer, and returns a PublishContext. This context needs to be used by listeners.
     */
    public static @Nullable PublishContext CreatePublishContext(String publisherName, @Nullable String message, Object... args) {
        //Validations
        Stack<BlockContext> stack = threadContextStack.get();
        if (stack == null || stack.isEmpty()) {
            log.warn("VFL block stack is null or empty");
            return null;
        }
        final VFLBuffer localBuffer = VFLAnnotation.buffer;
        if (localBuffer == null) {
            log.warn("VFL buffer is not initialized");
            return null;
        }

        BlockContext currentContext = stack.peek();
        String msg = null;
        if (message != null) {
            msg = CommonUtil.FormatMessage(message, args);
        }
        //Create publish block and push it
        Block publishBlock = new Block(publisherName, currentContext.getBlock().getId());
        localBuffer.pushBlock(publishBlock);
        //Create publish log and push it
        BlockLog publishLog = new BlockLog(msg, currentContext.getBlock().getId(), currentContext.getCurrentLogId(), publishBlock.getId(), LogTypeTraceBlock.PUBLISH_EVENT);
        localBuffer.pushLog(publishLog);
        //Set start and end time for the publish block
        localBuffer.pushBlockEntered(publishBlock.getId());
        localBuffer.pushBlockExited(publishBlock.getId());
        return new PublishContext(publishBlock);
    }

    /**
     * Creates a remote block and log, pushes them to buffer, and executes the provided function within the context of this remote block. To be used for remote external calls. This function notes down the time the function was completed. The other service needs to use {@link RemoteBlock} annotation to link. Doing so will set the entered and exited time
     */
    public static <R> @Nullable R RemoteBlock(String blockName, @Nullable String message, Function<RemoteBlockWrapper, R> fn) {
        //Validations
        Stack<BlockContext> stack = threadContextStack.get();
        if (stack == null || stack.isEmpty()) {
            log.warn("VFL block stack is null or empty");
            return null;
        }
        final VFLBuffer localBuffer = buffer;
        if (localBuffer == null) {
            log.warn("VFL buffer is not initialized");
            return null;
        }

        BlockContext currentContext = stack.peek();
        Block remoteBlock = new Block(blockName, currentContext.getBlock().getId());
        localBuffer.pushBlock(remoteBlock);
        BlockLog remoteLog = new BlockLog(message, currentContext.getBlock().getId(), currentContext.getCurrentLogId(), remoteBlock.getId(), LogTypeTraceBlock.REMOTE_TRACE);
        localBuffer.pushLog(remoteLog);
        try {
            //block entered and block exited will be handled by the other service using the RemoteBlock annotation
            return fn.apply(new RemoteBlockWrapper(remoteBlock));
        } catch (Exception e) {
            log.error("[VFL] Remote block failed", e);
            BlockLog errorLog = new BlockLog("Exception executing remote block ${e.getMessage()}", currentContext.getBlock().getId(), currentContext.getCurrentLogId(), LogTypeBase.ERROR);
            currentContext.setCurrentLogId(errorLog.getId());
            throw e;
        } finally {
            localBuffer.pushBlockReturned(remoteBlock.getId());
        }
    }

    // Nested static inner class for ByteBuddy agent setup
    private static class ByteBuddyInitializer {
        private static void initializeAgent() {
            Instrumentation inst = ByteBuddyAgent.install();
            AgentBuilder agentBuilder = new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);

            // Instrument methods annotated with @SubBlock
            agentBuilder = agentBuilder
                .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(SubBlock.class)))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    log.debug("[VFL] Instrumenting: {} for @SubBlock", typeDescription.getName());
                    return builder.visit(Advice.to(SubBlockAdvice.class).on(ElementMatchers.isAnnotatedWith(SubBlock.class).and(ElementMatchers.not(ElementMatchers.isAbstract()))));
                });

            // Instrument methods annotated with @RootBlock
            agentBuilder = agentBuilder
                .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(RootBlock.class)))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    log.debug("[VFL] Instrumenting: {} for @RootBlock", typeDescription.getName());
                    return builder.visit(Advice.to(RootBlockAdvice.class).on(ElementMatchers.isAnnotatedWith(RootBlock.class).and(ElementMatchers.not(ElementMatchers.isAbstract()))));
                });

            // Instrument methods annotated with @RemoteBlock
            agentBuilder = agentBuilder
                .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(RemoteBlock.class)))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    log.debug("[VFL] Instrumenting: {} for @RemoteBlock", typeDescription.getName());
                    return builder.visit(Advice.to(RemoteBlockAdvice.class).on(ElementMatchers.isAnnotatedWith(RemoteBlock.class).and(ElementMatchers.not(ElementMatchers.isAbstract()))));
                });

            // Instrument methods annotated with @EventListenerBlock
            agentBuilder = agentBuilder
                .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(EventListenerBlock.class)))
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    log.debug("[VFL] Instrumenting: {} for @EventListenerBlock", typeDescription.getName());
                    return builder.visit(Advice.to(EventListenerBlockAdvice.class).on(ElementMatchers.isAnnotatedWith(EventListenerBlock.class).and(ElementMatchers.not(ElementMatchers.isAbstract()))));
                });

            // Install all transformations
            agentBuilder.installOn(inst);
        }
    }
}
