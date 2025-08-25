package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.VFLBase;
import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.dto.PublishContext;
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
    //TODO Publish event block method that will return publisher context
    //TODO Remote block create method that will return remote block context
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
            // Attach ByteBuddy agent to JVM
            Instrumentation inst = ByteBuddyAgent.install();
            // Configure ByteBuddy to transform classes with @SubBlock annotated methods
            new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    // Match classes that declare any method annotated with @SubBlock
                    .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(SubBlock.class)))
                    // Inject advice into those annotated methods, excluding abstract ones
                    .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                        log.debug("[VFL] Attempting to instrument: {}", typeDescription.getName());
                        return builder.visit(Advice.to(SubBlockAdvice.class).on(ElementMatchers.isAnnotatedWith(SubBlock.class).and(ElementMatchers.not(ElementMatchers.isAbstract()))));
                    }).installOn(inst);

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

        Block publishBlock = new Block(publisherName, currentContext.getBlock().getId());
        localBuffer.pushBlock(publishBlock);
        BlockLog publishLog = new BlockLog(msg, currentContext.getBlock().getId(), currentContext.getCurrentLogId(), publishBlock.getId(), LogTypeTraceBlock.PUBLISH_EVENT);
        localBuffer.pushLog(publishLog);

        return new PublishContext(publishBlock);
    }

    /**
     * Creates a remote block and log, pushes them to buffer, and executes the provided function within the context of this remote block. To be used for remote external calls. This function notes down the time the function was completed. The other service needs to use {@link RemoteBlock} annotation to link. Doing so will set the entered and exited time
     */
    public static <R> @Nullable R RemoteVFL(String blockName, @Nullable String message, Function<Block, R> fn) {
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
            return fn.apply(remoteBlock);
        } catch (Exception e) {
            log.error("[VFL] Remote block failed", e);
            BlockLog errorLog = new BlockLog("Exception executing remote block ${e.getMessage()}", currentContext.getBlock().getId(), currentContext.getCurrentLogId(), LogTypeBase.ERROR);
            currentContext.setCurrentLogId(errorLog.getId());
            throw e;
        } finally {
            localBuffer.pushBlockReturned(remoteBlock.getId());
        }
    }
}
