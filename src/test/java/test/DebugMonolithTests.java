package test;

import dev.kuku.vfl.api.annotation.VFLAnnotation;
import dev.kuku.vfl.api.buffer.SynchronousBuffer;
import dev.kuku.vfl.api.buffer.flushHandler.VFLHubFlushHandler;
import dev.kuku.vfl.internal.buffer.VFLBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.FlowService;

import java.util.concurrent.ExecutionException;

public class DebugMonolithTests {

    @BeforeAll
    static void setupBytecodeManipulation() throws Exception {
        System.out.println("=== STARTING SETUP ===");

        // Initialize VFL directly instead of separate JVM
        VFLBuffer buffer = new SynchronousBuffer(
                new VFLHubFlushHandler("http://localhost:8080"),
                50
        );

        System.out.println("Buffer created: " + buffer);

        VFLAnnotation.instrument(buffer);
        System.out.println("VFL Instrumentation completed");

        // Add delay to ensure instrumentation completes
        Thread.sleep(5000); //if you have ass PC increase this to ensure everything is transformed
        System.out.println("=== SETUP COMPLETE ===");
    }

    private final FlowService flowService;

    DebugMonolithTests() {
        System.out.println("=== CREATING FlowService ===");
        this.flowService = new FlowService();
        System.out.println("FlowService created: " + flowService);
    }

    @Test
    void flat() {
        flowService.flatFlow();
    }

    @Test
    void linear() {
        flowService.linearFlow();
    }

    @Test
    void parallel() throws ExecutionException, InterruptedException {
        flowService.parallelFlow();
    }

    @Test
    void parallelSingleThread() throws ExecutionException, InterruptedException {
        flowService.parallelFlowButSingleBgThread();
    }
}