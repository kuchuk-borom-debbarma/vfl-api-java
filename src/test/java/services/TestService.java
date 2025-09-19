package services;

import dev.kuku.vfl.api.annotation.SubBlock;
import dev.kuku.vfl.api.annotation.VFLAnnotation;

public class TestService {
    private final VFLAnnotation logger = VFLAnnotation.getInstance();


    @SubBlock
    public int square(int num1) {
        logger.info("Squaring {}", num1);
        return num1 * num1;
    }

    @SubBlock
    public int pythagorean(int num1, int num2) {
        logger.info("Pythagorean {} + {}", num1, num2);
        int square1 = square(num1);
        int square2 = square(num2);
        return sum(square1, square2);
    }

    @SubBlock
    public int sum(int square1, int square2) {
        logger.info("Sum {} + {}", square1 + square2);
        return square1 + square2;
    }
}
