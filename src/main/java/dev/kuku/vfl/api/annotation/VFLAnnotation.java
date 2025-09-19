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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Stack;
import java.util.function.Function;

public class VFLAnnotation extends VFLBase {
    static VFLAnnotation INSTANCE = null;
    static final ThreadLocal<Stack<BlockContext>> threadContextStack = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(VFLAnnotation.class);
    static VFLBuffer buffer = null;

    private VFLAnnotation() {

    }

    @Override
    protected BlockContext getBlockContext() {
        final Stack<BlockContext> stack = threadContextStack.get();
        if (stack == null) {
            log.warn("VFL block stack is null");
            return null;
        }
        if (stack.isEmpty()) {
            log.warn("VFL block stack is empty");
            return null;
        }
        var currentStackContext = stack.peek();
        log.debug("Current stack context: {}", currentStackContext);
        return currentStackContext;
    }

    @Override
    protected VFLBuffer getVFLBuffer() {
        return VFLAnnotation.buffer;
    }

    public static VFLAnnotation getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new VFLAnnotation();
        }
        return INSTANCE;
    }

    public static synchronized void instrument(VFLBuffer buffer) {
        if (buffer == null) {
            log.warn("VFL buffer is null. Aborting initialization of VFL Annotation.");
            return;
        }
        VFLAnnotation.buffer = buffer;
        try {
            ByteBuddyInitializer.initializeAgent();
            log.info("[VFL] Instrumentation initialised successfully");
        } catch (Exception e) {
            log.error("[VFL] Initialisation failed", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * VFL Annotation initializer that will skip initialization. Useful for disabling VFL without modifying other part of the codebase
     */
    public static synchronized void instrument() {
        VFLAnnotation.instrument(null);
    }

    /**
     * Creates a publish event block and log, pushes them to buffer, and returns a PublishContext. This context needs to be used by listeners.
     */
    public static PublishContext CreatePublishContext(String publisherName, String message, Object... args) {
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
    public static <R> R RemoteBlock(String blockName, String message, Function<RemoteBlockWrapper, R> fn) {
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
        BlockLog remoteLog = new BlockLog(message, currentContext.getBlock().getId(), currentContext.getCurrentLogId(), remoteBlock.getId(), LogTypeTraceBlock.TRACE_REMOTE);
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
            AgentBuilder agentBuilder = new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(AgentBuilder.TypeStrategy.Default.REDEFINE);

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

            // Retransform already loaded classes that have our annotations
            try {
                Class<?>[] loadedClasses = inst.getAllLoadedClasses();
                log.debug("[VFL] Checking {} loaded classes for retransformation", loadedClasses.length);

                for (Class<?> loadedClass : loadedClasses) {
                    if (inst.isModifiableClass(loadedClass) && !loadedClass.isInterface() && !loadedClass.isAnnotation()) {
                        boolean hasAnnotatedMethods = false;

                        try {
                            // Check if class has methods with our annotations
                            for (Method method : loadedClass.getDeclaredMethods()) {
                                if (method.isAnnotationPresent(RootBlock.class) ||
                                    method.isAnnotationPresent(SubBlock.class) ||
                                    method.isAnnotationPresent(RemoteBlock.class) ||
                                    method.isAnnotationPresent(EventListenerBlock.class)) {
                                    hasAnnotatedMethods = true;
                                    log.debug("[VFL] Found annotated method: {}.{}", loadedClass.getSimpleName(), method.getName());
                                    break;
                                }
                            }

                            if (hasAnnotatedMethods) {
                                log.info("[VFL] Retransforming already loaded class: {}", loadedClass.getName());
                                inst.retransformClasses(loadedClass);
                            }
                        } catch (NoClassDefFoundError | SecurityException e) {
                            // Skip classes that can't be analyzed due to missing dependencies or security restrictions
                            log.debug("[VFL] Skipping class {} due to: {}", loadedClass.getName(), e.getMessage());
                        } catch (Exception e) {
                            log.warn("[VFL] Error checking class {} for annotations: {}", loadedClass.getName(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[VFL] Failed to retransform some classes", e);
                // Don't rethrow - partial success is better than complete failure
            }
        }
    }
}
