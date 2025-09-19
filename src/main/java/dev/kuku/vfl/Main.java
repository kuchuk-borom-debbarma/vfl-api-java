package dev.kuku.vfl;

import dev.kuku.vfl.api.annotation.VFLAnnotation;
import dev.kuku.vfl.api.buffer.SynchronousBuffer;
import dev.kuku.vfl.api.buffer.flushHandler.VFLHubFlushHandler;
import dev.kuku.vfl.internal.buffer.VFLBuffer;

public class Main {
    public static void main(String[] args) {
        VFLBuffer buffer = new SynchronousBuffer(
                new VFLHubFlushHandler(
                        "http://localhost:8080"
                ),
                50
        );
        VFLAnnotation.instrument(buffer);
        //TODO a way to disable certain actions easily, such as NoOp buffer, flushHandler or skipping if byteBuddy is not initialized
        //TODO null pointer safety checks that should not break the code
    }

}