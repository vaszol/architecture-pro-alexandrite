package com.example.servicea.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class CalculationService {

    private static final Logger logger = LoggerFactory.getLogger(CalculationService.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("service-a");

    @Autowired
    private RestTemplate restTemplate;

    @Value("${service.b.url}")
    private String serviceBUrl;

    /**
     * Вызывает Service-B для выполнения вычислений
     */
    public Map<String, Object> callServiceB(String numbers) {
        Span span = tracer.spanBuilder("call-service-b")
                .startSpan();

        try {
            span.makeCurrent();

            logger.info("Service-A: Вызываю Service-B с числами: {}", numbers);

            // Добавляем атрибуты для трассировки
            span.setAttribute("service-b.url", serviceBUrl);
            span.setAttribute("service-b.numbers", numbers);

            // Формируем URL для вызова Service-B
            String url = serviceBUrl + "/calculate?numbers=" + numbers;

            // Делаем HTTP-вызов к Service-B
            // OpenTelemetry автоматически добавит трассировочные заголовки!
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            // Логируем результат
            Map<String, Object> result = response.getBody();
            logger.info("Service-A: Получен ответ от Service-B: {}", result);

            // Добавляем информацию о вызове в спан
            if (response.getStatusCode().is2xxSuccessful()) {
                span.setAttribute("service-b.status", "success");
                span.setAttribute("service-b.status_code", response.getStatusCode().value()); // ИСПРАВЛЕНО

                if (result != null) {
                    span.setAttribute("service-b.result.sum",
                            result.get("sum") != null ? result.get("sum").toString() : "null");
                }
            }

            return result != null ? result : new HashMap<>();

        } catch (HttpStatusCodeException e) {
            logger.error("Service-A: Ошибка при вызове Service-B: {}", e.getStatusCode());
            span.recordException(e);
            span.setAttribute("service-b.status", "error");
            span.setAttribute("service-b.status_code", e.getStatusCode().value()); // ИСПРАВЛЕНО

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Service-B вернул ошибку: " + e.getStatusCode());
            errorResponse.put("message", e.getResponseBodyAsString());
            return errorResponse;

        } catch (Exception e) {
            logger.error("Service-A: Неожиданная ошибка при вызове Service-B: {}", e.getMessage());
            span.recordException(e);
            span.setAttribute("service-b.status", "exception");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Ошибка при вызове Service-B");
            errorResponse.put("message", e.getMessage());
            return errorResponse;

        } finally {
            span.end();
        }
    }

    /**
     * Проверяет доступность Service-B
     */
    public Map<String, Object> checkServiceBHealth() {
        Span span = tracer.spanBuilder("check-service-b-health")
                .startSpan();

        try {
            span.makeCurrent();

            String url = serviceBUrl + "/health";
            logger.debug("Service-A: Проверяю здоровье Service-B: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            Map<String, Object> result = new HashMap<>();
            result.put("service-a", "UP");
            result.put("service-b", response.getBody());
            result.put("timestamp", System.currentTimeMillis());

            span.setAttribute("health.check.success", true);

            return result;

        } catch (Exception e) {
            logger.warn("Service-A: Service-B недоступен: {}", e.getMessage());
            span.recordException(e);

            Map<String, Object> result = new HashMap<>();
            result.put("service-a", "UP");
            result.put("service-b", "DOWN");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());

            return result;

        } finally {
            span.end();
        }
    }

    /**
     * Простой вызов Service-B
     */
    public String callSimpleServiceB() {
        Span span = tracer.spanBuilder("call-simple-service-b")
                .startSpan();

        try {
            span.makeCurrent();

            String url = serviceBUrl + "/simple";
            logger.debug("Service-A: Делаю простой вызов Service-B");

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            span.setAttribute("simple.call.success", true);
            return response.getBody();

        } catch (Exception e) {
            logger.error("Service-A: Ошибка простого вызова: {}", e.getMessage());
            span.recordException(e);
            return "Error calling Service-B: " + e.getMessage();

        } finally {
            span.end();
        }
    }
}