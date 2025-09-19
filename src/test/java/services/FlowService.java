package services;

import dev.kuku.vfl.api.annotation.RootBlock;
import dev.kuku.vfl.api.annotation.VFLAnnotation;

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
        int square = num *2;
        logger.info("Square = {}", square);
        logger.info("Complete flat flow....");
    }
}
