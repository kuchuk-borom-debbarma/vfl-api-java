package dev.kuku.vfl.api.annotation;

import dev.kuku.vfl.internal.buffer.VFLBuffer;

import static dev.kuku.vfl.api.annotation.AnnotationData.instance;

public class VFLAnnotation {
    public static void Setup(VFLBuffer buffer) {
        if (instance == null) {
            instance = new AnnotationData(buffer);
        }
    }
}
