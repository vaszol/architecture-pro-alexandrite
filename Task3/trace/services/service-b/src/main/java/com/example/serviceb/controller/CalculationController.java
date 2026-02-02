package com.example.serviceb.controller;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
public class CalculationController {

    private static final Logger logger = LoggerFactory.getLogger(CalculationController.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("service-b");
    private final Random random = new Random();

    /**
     * Пример GET API для вычислений
     * GET /calculate?numbers=10,20,30
     */
    @GetMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(
            @RequestParam(value = "numbers", defaultValue = "1,2,3,4,5") String numbers) {

        // Создаем спан для этого метода
        Span span = tracer.spanBuilder("calculate-operation")
                .startSpan();

        try {
            // Делаем спан активным
            span.makeCurrent();

            logger.info("Service-B: Получен запрос на вычисление для чисел: {}", numbers);

            // Парсим числа
            String[] numberStrings = numbers.split(",");
            double[] values = new double[numberStrings.length];

            for (int i = 0; i < numberStrings.length; i++) {
                values[i] = Double.parseDouble(numberStrings[i].trim());
            }

            // Выполняем различные вычисления
            double sum = calculateSum(values);
            span.addEvent("Сумма вычислена: " + sum);

            double average = calculateAverage(values);
            span.addEvent("Среднее вычислено: " + average);

            double max = findMax(values);

            // Имитируем обработку (задержку)
            Thread.sleep(random.nextInt(100) + 50);

            // Создаем ответ
            Map<String, Object> response = new HashMap<>();
            response.put("service", "service-b");
            response.put("operation", "calculation");
            response.put("input_numbers", numbers);
            response.put("sum", sum);
            response.put("average", average);
            response.put("max", max);
            response.put("timestamp", System.currentTimeMillis());

            // Добавляем атрибуты в спан для лучшей видимости в Jaeger
            span.setAttribute("calculation.input.count", values.length);
            span.setAttribute("calculation.result.sum", sum);
            span.setAttribute("calculation.result.average", average);

            logger.info("Service-B: Вычисления завершены. Сумма: {}, Среднее: {}", sum, average);

            return ResponseEntity.ok(response);

        } catch (NumberFormatException e) {
            logger.error("Service-B: Ошибка парсинга чисел: {}", e.getMessage());
            span.recordException(e);
            span.setAttribute("error", true);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Неверный формат чисел. Используйте: numbers=1,2,3");
            errorResponse.put("example", "/calculate?numbers=10,20,30");
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            span.recordException(e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Обработка прервана");
            return ResponseEntity.status(500).body(errorResponse);

        } catch (Exception e) {
            logger.error("Service-B: Неожиданная ошибка: {}", e.getMessage());
            span.recordException(e);
            span.setAttribute("error", true);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Внутренняя ошибка сервера");
            return ResponseEntity.status(500).body(errorResponse);

        } finally {
            // Всегда завершаем спан
            span.end();
        }
    }

    /**
     * Вспомогательный метод для вычисления суммы
     */
    private double calculateSum(double[] numbers) {
        Span span = tracer.spanBuilder("calculate-sum")
                .startSpan();

        try {
            double sum = 0;
            for (double num : numbers) {
                sum += num;
            }
            span.setAttribute("sum.result", sum);
            return sum;
        } finally {
            span.end();
        }
    }

    /**
     * Вспомогательный метод для вычисления среднего
     */
    private double calculateAverage(double[] numbers) {
        Span span = tracer.spanBuilder("calculate-average")
                .startSpan();

        try {
            if (numbers.length == 0) {
                span.setAttribute("average.result", 0.0);
                return 0.0;
            }

            double sum = calculateSum(numbers);
            double average = sum / numbers.length;
            span.setAttribute("average.result", average);
            return average;
        } finally {
            span.end();
        }
    }

    /**
     * Вспомогательный метод для поиска максимума
     */
    private double findMax(double[] numbers) {
        Span span = tracer.spanBuilder("find-max")
                .startSpan();

        try {
            if (numbers.length == 0) {
                span.setAttribute("max.result", 0.0);
                return 0.0;
            }

            double max = numbers[0];
            for (int i = 1; i < numbers.length; i++) {
                if (numbers[i] > max) {
                    max = numbers[i];
                }
            }

            span.setAttribute("max.result", max);
            return max;
        } finally {
            span.end();
        }
    }

    /**
     * Дополнительный endpoint для проверки здоровья
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "service-b");
        health.put("timestamp", System.currentTimeMillis());

        logger.debug("Service-B: Проверка здоровья");
        return ResponseEntity.ok(health);
    }

    /**
     * Простой endpoint для тестирования
     */
    @GetMapping("/simple")
    public ResponseEntity<String> simple() {
        Span span = tracer.spanBuilder("simple-operation")
                .startSpan();

        try {
            span.makeCurrent();
            span.setAttribute("endpoint", "simple");

            logger.info("Service-B: Простой endpoint вызван");

            // Имитируем работу
            Thread.sleep(30);

            return ResponseEntity.ok("Hello from Service-B! Calculation service is ready.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body("Error");
        } finally {
            span.end();
        }
    }
}