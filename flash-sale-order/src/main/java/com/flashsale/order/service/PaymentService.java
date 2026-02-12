package com.flashsale.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock Payment Service.
 *
 * Simulates a payment gateway with configurable outcomes:
 *   - SUCCESS  → payment confirmed
 *   - FAILURE  → payment declined
 *   - TIMEOUT  → gateway timeout (treated as failure for safety)
 *
 * In production, this would integrate with Stripe/Alipay/WeChat Pay.
 * The mock includes a 50ms simulated latency to mimic real gateway behavior.
 *
 * After processing, it publishes payment result events to Kafka topics:
 *   - PAYMENT_SUCCESS → consumed by InventoryConsumer (DB stock deduction)
 *   - PAYMENT_FAILED  → consumed by InventoryConsumer (Redis stock rollback)
 *
 * All events carry orderId and productId for idempotent processing.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public enum PaymentResult {
        SUCCESS, FAILURE, TIMEOUT
    }

    /**
     * Configurable: which result to simulate.
     * Default is SUCCESS. Can be changed at runtime via the admin API.
     */
    private volatile PaymentResult configuredResult = PaymentResult.SUCCESS;

    /** Simulated gateway latency in ms */
    private volatile int simulatedDelayMs = 50;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Process payment for an order.
     *
     * @param orderId   the order to pay
     * @param productId the product (for event payload)
     * @return the payment result
     */
    public PaymentResult pay(Long orderId, Long productId) {
        log.info("[PaymentService] Processing payment for orderId={}", orderId);

        // Simulate gateway latency
        try {
            Thread.sleep(simulatedDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        PaymentResult result = configuredResult;
        log.info("[PaymentService] Payment result for orderId={}: {}", orderId, result);

        try {
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("orderId", orderId);
            eventPayload.put("productId", productId);
            eventPayload.put("quantity", 1);
            String json = objectMapper.writeValueAsString(eventPayload);

            switch (result) {
                case SUCCESS -> {
                    // Update order status to PAID
                    orderRepository.updateStatus(orderId, Order.STATUS_PENDING_PAYMENT, Order.STATUS_PAID);
                    // Publish payment success event
                    kafkaTemplate.send("PAYMENT_SUCCESS", json);
                    log.info("[PaymentService] Published PAYMENT_SUCCESS for orderId={}", orderId);
                }
                case FAILURE, TIMEOUT -> {
                    // Update order status to CANCELED
                    orderRepository.updateStatus(orderId, Order.STATUS_PENDING_PAYMENT, Order.STATUS_CANCELED);
                    // Publish payment failure event → triggers stock rollback
                    kafkaTemplate.send("PAYMENT_FAILED", json);
                    log.info("[PaymentService] Published PAYMENT_FAILED for orderId={}", orderId);
                }
            }
        } catch (Exception e) {
            log.error("[PaymentService] Error processing payment result", e);
        }

        return result;
    }

    // --- Configuration methods (called by admin API) ---

    public void setConfiguredResult(PaymentResult result) {
        this.configuredResult = result;
        log.info("[PaymentService] Configured result changed to: {}", result);
    }

    public PaymentResult getConfiguredResult() {
        return configuredResult;
    }

    public void setSimulatedDelayMs(int delayMs) {
        this.simulatedDelayMs = delayMs;
    }

    public int getSimulatedDelayMs() {
        return simulatedDelayMs;
    }
}
