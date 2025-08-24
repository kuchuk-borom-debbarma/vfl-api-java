package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Stack;

public class AnnotationData {
    public static @MonotonicNonNull AnnotationData instance;
    public final ThreadLocal<@Nullable Stack<BlockContext>> threadContextStack;
    public final VFLBuffer buffer;

    public AnnotationData(VFLBuffer buffer) {
        this.threadContextStack = new ThreadLocal<>();
        this.buffer = buffer;
    }

    public static void Init(VFLBuffer buffer) {
        if (instance == null) {
            instance = new AnnotationData(buffer);
        }
    }
}
