package com.example.servicea.controller;

import com.example.servicea.service.CalculationService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("service-a");

    @Autowired
    private CalculationService calculationService;

    private final Random random = new Random();

    /**
     * Основной endpoint для заказов, который вызывает Service-B
     * GET /order?items=3&amount=100.50
     */
    @GetMapping("/order")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestParam(value = "items", defaultValue = "1") int items,
            @RequestParam(value = "amount", defaultValue = "100.0") double amount) {

        Span span = tracer.spanBuilder("create-order")
                .startSpan();

        try {
            span.makeCurrent();

            logger.info("Service-A: Создание заказа. Товары: {}, Сумма: {}", items, amount);

            // Имитируем обработку заказа
            Thread.sleep(random.nextInt(50) + 20);

            // Генерируем ID заказа
            String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

            // Создаем строку чисел для вычислений (на основе параметров заказа)
            String numbersForCalculation = generateNumbersForCalculation(items, amount);
            span.addEvent("Сгенерированы числа для вычислений: " + numbersForCalculation);

            // Вызываем Service-B для вычислений
            Map<String, Object> calculationResult = calculationService.callServiceB(numbersForCalculation);

            // Создаем финальный ответ
            Map<String, Object> response = createOrderResponse(orderId, items, amount, calculationResult);

            // Добавляем атрибуты трассировки
            span.setAttribute("order.id", orderId);
            span.setAttribute("order.items", items);
            span.setAttribute("order.amount", amount);
            span.setAttribute("order.status", "completed");
            span.setAttribute("service.b.called", true);

            if (calculationResult.containsKey("sum")) {
                span.setAttribute("calculation.sum", calculationResult.get("sum").toString());
            }

            logger.info("Service-A: Заказ {} успешно создан", orderId);

            return ResponseEntity.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Service-A: Обработка заказа прервана");

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Обработка заказа прервана");
            return ResponseEntity.status(500).body(error);

        } catch (Exception e) {
            logger.error("Service-A: Ошибка при создании заказа: {}", e.getMessage());
            span.recordException(e);
            span.setAttribute("error", true);

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Ошибка при создании заказа");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);

        } finally {
            span.end();
        }
    }

    /**
     * Простой endpoint для проверки
     */
    @GetMapping("/")
    public ResponseEntity<String> home() {
        Span span = tracer.spanBuilder("home-endpoint")
                .startSpan();

        try {
            span.makeCurrent();

            String message = "Service-A (Order Service) is running!\n" +
                    "Available endpoints:\n" +
                    "  GET /order?items=3&amount=100.50 - Create order and call Service-B\n" +
                    "  GET /health - Health check\n" +
                    "  GET /test - Test Service-B connection\n" +
                    "  GET /trace-test - Manual trace test";

            return ResponseEntity.ok(message);
        } finally {
            span.end();
        }
    }

    /**
     * Проверка здоровья с вызовом Service-B
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Span span = tracer.spanBuilder("health-check")
                .startSpan();

        try {
            span.makeCurrent();

            Map<String, Object> health = calculationService.checkServiceBHealth();
            health.put("service", "service-a");
            health.put("endpoint", "/health");

            logger.debug("Service-A: Проверка здоровья выполнена");

            return ResponseEntity.ok(health);
        } finally {
            span.end();
        }
    }

    /**
     * Тестовый endpoint для проверки связи с Service-B
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testServiceB() {
        Span span = tracer.spanBuilder("test-service-b")
                .startSpan();

        try {
            span.makeCurrent();

            logger.info("Service-A: Тестирую соединение с Service-B");

            // Простой вызов Service-B
            String simpleResponse = calculationService.callSimpleServiceB();

            // Вызов Service-B для вычислений
            String testNumbers = "10,20,30,40,50";
            Map<String, Object> calculationResult = calculationService.callServiceB(testNumbers);

            Map<String, Object> response = new HashMap<>();
            response.put("service-a", "running");
            response.put("service-b-simple", simpleResponse);
            response.put("service-b-calculation", calculationResult);
            response.put("test-numbers", testNumbers);
            response.put("timestamp", System.currentTimeMillis());

            span.setAttribute("test.success", true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Service-A: Тест не пройден: {}", e.getMessage());
            span.recordException(e);

            Map<String, Object> error = new HashMap<>();
            error.put("error", "Тест не пройден");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        } finally {
            span.end();
        }
    }

    /**
     * Endpoint для ручного тестирования трассировки
     */
    @GetMapping("/trace-test")
    public ResponseEntity<Map<String, Object>> traceTest() {
        Span span = tracer.spanBuilder("manual-trace-test")
                .startSpan();

        try {
            span.makeCurrent();
            span.setAttribute("manual.test", true);

            logger.info("Service-A: Запущен ручной тест трассировки");

            // Создаем вложенные спаны для демонстрации
            Span childSpan1 = tracer.spanBuilder("child-operation-1")
                    .startSpan();
            try {
                childSpan1.makeCurrent();
                Thread.sleep(20);
                childSpan1.addEvent("Первая операция завершена");
            } finally {
                childSpan1.end();
            }

            // Вызываем Service-B
            String response = calculationService.callSimpleServiceB();

            Span childSpan2 = tracer.spanBuilder("child-operation-2")
                    .startSpan();
            try {
                childSpan2.makeCurrent();
                Thread.sleep(30);
                childSpan2.addEvent("Вторая операция завершена");
            } finally {
                childSpan2.end();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("test", "manual-trace-test");
            result.put("service-b-response", response);
            result.put("trace-id", span.getSpanContext().getTraceId());
            result.put("span-id", span.getSpanContext().getSpanId());
            result.put("timestamp", System.currentTimeMillis());

            logger.info("Service-A: Ручной тест завершен. Trace ID: {}", span.getSpanContext().getTraceId());

            return ResponseEntity.ok(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(Map.of("error", "Тест прерван"));
        } catch (Exception e) {
            span.recordException(e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    /**
     * Генерирует числа для вычислений на основе параметров заказа
     */
    private String generateNumbersForCalculation(int items, double amount) {
        StringBuilder numbers = new StringBuilder();

        // Генерируем числа на основе параметров заказа
        numbers.append(items).append(",");
        numbers.append(String.format("%.0f", amount)).append(",");

        // Добавляем несколько случайных чисел
        for (int i = 0; i < 3; i++) {
            if (i > 0) numbers.append(",");
            numbers.append(random.nextInt(100) + 1);
        }

        return numbers.toString();
    }

    /**
     * Создает ответ с информацией о заказе
     */
    private Map<String, Object> createOrderResponse(String orderId, int items, double amount,
                                                    Map<String, Object> calculationResult) {
        Map<String, Object> response = new HashMap<>();

        response.put("service", "service-a");
        response.put("operation", "order-creation");
        response.put("order_id", orderId);
        response.put("items_count", items);
        response.put("total_amount", amount);
        response.put("status", "completed");
        response.put("timestamp", System.currentTimeMillis());

        // Добавляем результаты из Service-B
        response.put("calculation_service", "service-b");
        response.put("calculation_result", calculationResult);

        // Добавляем итоговую стоимость (пример бизнес-логики)
        double calculationSum = calculationResult.containsKey("sum") ?
                Double.parseDouble(calculationResult.get("sum").toString()) : 0;
        double finalAmount = amount + (calculationSum * 0.1); // Пример расчета
        response.put("final_amount", finalAmount);

        return response;
    }
}