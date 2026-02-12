package com.flashsale.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.inventory.entity.InventoryStock;
import com.flashsale.inventory.entity.StockLog;
import com.flashsale.inventory.repository.InventoryStockRepository;
import com.flashsale.inventory.repository.StockLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Kafka consumer for payment result events.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  PAYMENT EVENT FLOW                                              │
 * │                                                                  │
 * │  On PAYMENT_SUCCESS:                                             │
 * │    1. Check idempotency (stock_log already DEDUCTED?)            │
 * │    2. Deduct DB stock (inventory_stock)                          │
 * │    3. Update stock_log.status = DEDUCTED                         │
 * │    → DB is now consistent with Redis pre-deduction               │
 * │                                                                  │
 * │  On PAYMENT_FAILED:                                              │
 * │    1. Check idempotency (stock_log already ROLLED_BACK?)         │
 * │    2. Restore Redis stock (INCR)                                 │
 * │    3. Update stock_log.status = ROLLED_BACK                      │
 * │    4. Clear JVM sold-out flag (product may be purchasable again) │
 * │    → Do NOT deduct DB stock (it was never touched)               │
 * │                                                                  │
 * │  WHY NOT DEDUCT DB ON ORDER CREATION:                            │
 * │  If we deducted DB stock at order time and the user never pays,  │
 * │  we'd need a complex rollback timer. Instead, we only lock       │
 * │  stock in Redis (cheap, fast) and defer DB deduction until       │
 * │  payment is confirmed. This is the "reserve then confirm"        │
 * │  pattern from saga-based architectures.                          │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Service
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    @Autowired
    private StockLogRepository stockLogRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private InventoryServiceImpl inventoryService;

    /**
     * Handle payment success events.
     * Deduct DB stock and finalize the stock_log.
     */
    @KafkaListener(topics = "PAYMENT_SUCCESS", groupId = "inventory-group")
    @Transactional
    public void handlePaymentSuccess(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = json.get("orderId").asLong();
            Long productId = json.get("productId").asLong();
            int quantity = json.has("quantity") ? json.get("quantity").asInt() : 1;

            log.info("[InventoryConsumer] PAYMENT_SUCCESS received: orderId={}, productId={}", orderId, productId);

            // ── Idempotency check ──
            // If stock_log for this order is already DEDUCTED, skip.
            // This handles duplicate Kafka messages (at-least-once delivery).
            Optional<StockLog> existingLog = stockLogRepository.findByOrderId(orderId);
            if (existingLog.isPresent() && existingLog.get().getStatus() == StockLog.STATUS_DEDUCTED) {
                log.warn("[InventoryConsumer] Duplicate PAYMENT_SUCCESS for orderId={}. Skipping.", orderId);
                return;
            }

            // ── Deduct DB stock ──
            int affected = inventoryStockRepository.deductStock(productId, quantity);
            if (affected == 0) {
                log.error("[InventoryConsumer] DB stock deduction failed for product={}, orderId={}. " +
                          "DB stock may be inconsistent with Redis!", productId, orderId);
                // In production: trigger an alert. The Redis pre-deduction succeeded
                // but DB doesn't have enough stock — this shouldn't happen in normal flow.
                return;
            }

            // ── Update stock_log → DEDUCTED ──
            if (existingLog.isPresent()) {
                StockLog sl = existingLog.get();
                sl.setStatus(StockLog.STATUS_DEDUCTED);
                stockLogRepository.save(sl);
            }

            log.info("[InventoryConsumer] DB stock deducted for product={}, orderId={}", productId, orderId);

        } catch (Exception e) {
            log.error("[InventoryConsumer] Error processing PAYMENT_SUCCESS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process payment success", e);
        }
    }

    /**
     * Handle payment failure / timeout events.
     * Restore Redis stock and mark stock_log as rolled back.
     * Do NOT deduct DB stock.
     */
    @KafkaListener(topics = "PAYMENT_FAILED", groupId = "inventory-group")
    @Transactional
    public void handlePaymentFailed(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            Long orderId = json.get("orderId").asLong();
            Long productId = json.get("productId").asLong();
            int quantity = json.has("quantity") ? json.get("quantity").asInt() : 1;

            log.info("[InventoryConsumer] PAYMENT_FAILED received: orderId={}, productId={}", orderId, productId);

            // ── Idempotency check ──
            Optional<StockLog> existingLog = stockLogRepository.findByOrderId(orderId);
            if (existingLog.isPresent() && existingLog.get().getStatus() == StockLog.STATUS_ROLLED_BACK) {
                log.warn("[InventoryConsumer] Duplicate PAYMENT_FAILED for orderId={}. Skipping.", orderId);
                return;
            }

            // ── Restore Redis stock (compensating action) ──
            String redisKey = "product:stock:" + productId;
            redisTemplate.opsForValue().increment(redisKey, quantity);
            log.info("[InventoryConsumer] Redis stock restored: key={}, +{}", redisKey, quantity);

            // ── Clear JVM sold-out flag ──
            // The product may now be purchasable again since stock was returned.
            inventoryService.clearSoldOutFlag(productId);

            // ── Update stock_log → ROLLED_BACK ──
            if (existingLog.isPresent()) {
                StockLog sl = existingLog.get();
                sl.setStatus(StockLog.STATUS_ROLLED_BACK);
                stockLogRepository.save(sl);
            }

            log.info("[InventoryConsumer] Stock rolled back for product={}, orderId={}", productId, orderId);

        } catch (Exception e) {
            log.error("[InventoryConsumer] Error processing PAYMENT_FAILED: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process payment failure", e);
        }
    }
}
