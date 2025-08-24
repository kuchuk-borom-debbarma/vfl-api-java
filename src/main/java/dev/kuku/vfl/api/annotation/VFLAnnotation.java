package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.VFLBase;
import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.Stack;

public class VFLAnnotation extends VFLBase {
    static final ThreadLocal<@Nullable Stack<BlockContext>> threadContextStack = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(VFLAnnotation.class);
    static VFLBuffer buffer;

    public VFLAnnotation(VFLBuffer buffer) {
        VFLAnnotation.buffer = buffer;
    }

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
    protected VFLBuffer getVFLBuffer() {
        return VFLAnnotation.buffer;
    }

    public static synchronized void initialize(VFLBuffer buffer) {
        VFLAnnotation.buffer = buffer;
        try {
            // Attach ByteBuddy agent to JVM
            Instrumentation inst = ByteBuddyAgent.install();
            // Configure ByteBuddy to transform classes with @SubBlock annotated methods
            new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    // Match classes that declare any method annotated with @SubBlock
                    .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(SubBlock.class)))
                    // Inject advice into those annotated methods, excluding abstract ones
                    .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                        log.debug("[VFL] Attempting to instrument: {}", typeDescription.getName());
                        return builder.visit(
                                Advice.to(VFLAdvice.class)
                                        .on(ElementMatchers.isAnnotatedWith(SubBlock.class)
                                                .and(ElementMatchers.not(ElementMatchers.isAbstract())))
                        );
                    })
                    .installOn(inst);

            log.info("[VFL] Instrumentation initialised successfully");
        } catch (Exception e) {
            log.error("[VFL] Initialisation failed", e);
            throw new RuntimeException(e);
        }
    }

    public static synchronized void initialize() {
        VFLAnnotation.initialize(null);
    }
}
