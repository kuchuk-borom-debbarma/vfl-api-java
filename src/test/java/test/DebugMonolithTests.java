package test;

import dev.kuku.vfl.api.annotation.VFLAnnotation;
import dev.kuku.vfl.api.buffer.SynchronousBuffer;
import dev.kuku.vfl.api.buffer.flushHandler.VFLHubFlushHandler;
import dev.kuku.vfl.internal.buffer.VFLBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.FlowService;

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
    void flat(){
        System.out.println("====== STARTING FLAT TEST ======");
        System.out.flush();

        flowService.flatFlow();

        System.out.println("FLAT FLOW TEST COMPLETE");
    }

    @Test
    void linear() {
        System.out.println("=== STARTING TEST ===");
        System.out.println("About to call linearFlow()");
        System.out.flush();

        flowService.linearFlow();

        System.out.println("linearFlow() completed");
        System.out.println("=== TEST COMPLETE ===");
    }
}