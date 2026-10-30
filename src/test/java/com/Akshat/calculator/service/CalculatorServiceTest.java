package com.Akshat.calculator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.Akshat.calculator.exception.DivisionByZeroException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CalculatorServiceTest {

    private static final double DELTA = 1e-9;

    private CalculatorService calculatorService;

    @BeforeEach
    void setUp() {
        calculatorService = new CalculatorService();
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        void addsPositiveNumbers() {
            assertEquals(5.0, calculatorService.add(2, 3), DELTA);
        }

        @Test
        void addsNegativeNumbers() {
            assertEquals(-7.0, calculatorService.add(-4, -3), DELTA);
        }

        @Test
        void addsDecimals() {
            assertEquals(0.3, calculatorService.add(0.1, 0.2), DELTA);
        }
    }

    @Nested
    @DisplayName("subtract")
    class Subtract {

        @Test
        void subtractsPositiveNumbers() {
            assertEquals(6.0, calculatorService.subtract(10, 4), DELTA);
        }

        @Test
        void subtractsNegativeNumbers() {
            assertEquals(-1.0, calculatorService.subtract(-4, -3), DELTA);
        }

        @Test
        void subtractsDecimals() {
            assertEquals(1.25, calculatorService.subtract(3.75, 2.5), DELTA);
        }
    }

    @Nested
    @DisplayName("multiply")
    class Multiply {

        @Test
        void multipliesPositiveNumbers() {
            assertEquals(42.0, calculatorService.multiply(6, 7), DELTA);
        }

        @Test
        void multipliesNegativeNumbers() {
            assertEquals(-12.0, calculatorService.multiply(-3, 4), DELTA);
            assertEquals(12.0, calculatorService.multiply(-3, -4), DELTA);
        }

        @Test
        void multipliesDecimals() {
            assertEquals(3.75, calculatorService.multiply(1.5, 2.5), DELTA);
        }

        @Test
        void multiplyByZeroIsZero() {
            assertEquals(0.0, calculatorService.multiply(123.45, 0), DELTA);
        }
    }

    @Nested
    @DisplayName("divide")
    class Divide {

        @Test
        void dividesPositiveNumbers() {
            assertEquals(5.0, calculatorService.divide(10, 2), DELTA);
        }

        @Test
        void dividesNegativeNumbers() {
            assertEquals(-2.5, calculatorService.divide(-5, 2), DELTA);
            assertEquals(2.5, calculatorService.divide(-5, -2), DELTA);
        }

        @Test
        void dividesDecimals() {
            assertEquals(3.0, calculatorService.divide(7.5, 2.5), DELTA);
        }

        @Test
        void divisionByZeroThrows() {
            DivisionByZeroException ex = assertThrows(DivisionByZeroException.class,
                    () -> calculatorService.divide(10, 0));
            assertEquals("Division by zero is not allowed", ex.getMessage());
        }

        @Test
        void divisionOfZeroByZeroThrows() {
            assertThrows(DivisionByZeroException.class, () -> calculatorService.divide(0, 0));
        }
    }
}
