package com.flashsale.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Kafka consumer for ORDER_CREATED events.
 *
 * When an order is created (status = PENDING_PAYMENT), this consumer
 * triggers the mock payment process. In production, this would call
 * a real payment gateway or wait for a webhook callback.
 *
 * IDEMPOTENCY:
 * Before processing, we check if the order is still in PENDING_PAYMENT
 * status. If it's already PAID or CANCELED, the event is a duplicate
 * and we skip it. This handles Kafka's at-least-once delivery semantics.
 */
@Service
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentService paymentService;

    @KafkaListener(topics = "ORDER_CREATED", groupId = "order-group")
    @Transactional
    public void handleOrderCreated(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = json.get("orderId").asLong();
            Long productId = json.get("productId").asLong();

            log.info("[OrderConsumer] ORDER_CREATED received: orderId={}, productId={}", orderId, productId);

            // ── Idempotency check ──
            // Only process if order is in PENDING_PAYMENT status
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("[OrderConsumer] Order not found: orderId={}", orderId);
                return;
            }

            Order order = orderOpt.get();
            if (order.getStatus() != Order.STATUS_PENDING_PAYMENT) {
                log.warn("[OrderConsumer] Order {} is in status {}, not PENDING_PAYMENT. Skipping.",
                        orderId, order.getStatus());
                return;
            }

            // ── Trigger payment ──
            PaymentService.PaymentResult result = paymentService.pay(orderId, productId);
            log.info("[OrderConsumer] Payment completed: orderId={}, result={}", orderId, result);

        } catch (Exception e) {
            log.error("[OrderConsumer] Error processing ORDER_CREATED: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process order created event", e);
        }
    }
}
