package services;

import dev.kuku.vfl.api.annotation.RootBlock;
import dev.kuku.vfl.api.annotation.VFLAnnotation;
import dev.kuku.vfl.api.annotation.VFLCompletableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class FlowService {
    private final TestService service;
    private final VFLAnnotation logger = VFLAnnotation.getInstance();

    public FlowService() {
        this.service = new TestService();
    }

    @RootBlock
    public void linearFlow() {
        logger.info("Starting simple linear flow...");
        int num1 = 12;
        logger.info("Num 1 = {}", num1);
        logger.info("Calculating square of {}", num1);
        int num2 = service.square(num1);
        int pythagoreanValue = service.pythagorean(num1, num2);
        logger.info("Pythagorean = {}", pythagoreanValue);
    }

    @RootBlock
    public void flatFlow() {
        logger.info("Starting Flat flow...");
        int num = 12;
        logger.info("Num = {}", num);
        int square = num * 2;
        logger.info("Square = {}", square);
        logger.info("Complete flat flow....");
    }

    @RootBlock
    public void parallelFlow() throws InterruptedException, ExecutionException {
        logger.info("Starting parallel flow...");
        var task1 = VFLCompletableFuture.runAsync("Task1",
                "Starting task 1", () -> {
                    logger.info("Starting task 1");
                    try {
                        service.sum(1, 2);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    logger.info("task 1 completed");
                });
        var task2 = VFLCompletableFuture.runAsync("Task2",
                null, () -> service.square(12));
        Thread.sleep(1000);
        task1.get();
        task2.get();
        logger.info("Completed parallel flow");
    }

    @RootBlock
    public void parallelFlowButSingleBgThread() throws ExecutionException, InterruptedException {
        logger.info("Starting parallel flow...");
        var t = Executors.newSingleThreadExecutor();
        var task1 = VFLCompletableFuture.runAsync("Task1",
                "Starting task 1", () -> {
                    logger.info("Starting task 1");
                    try {
                        service.square(12);
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    logger.info("task 1 completed");
                }, t);
        var task2 = VFLCompletableFuture.runAsync("Task2",
                null, () -> {
                    logger.info("Starting task 2");
                    try {
                        service.sum(12, 12);
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    logger.info("task 2 completed");
                }, t);
        Thread.sleep(1000);
        task1.get();
        task2.get();
        logger.info("Completed parallel flow");
    }

    @RootBlock
    public void longRunningOperation() {
        logger.info("Starting long running operation...");
        int time = 0;
        while (time < 10){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            time++;
            logger.info("Long running operation... {} seconds", time);
        }
        logger.info("Completed long running operation");
    }
}
