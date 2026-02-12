package com.flashsale.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.grpc.DeductStockRequest;
import com.flashsale.common.grpc.DeductStockResponse;
import com.flashsale.common.grpc.InventoryServiceRPCGrpc;
import com.flashsale.order.entity.LocalMessage;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.LocalMessageRepository;
import com.flashsale.order.repository.OrderRepository;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core order service implementing the enterprise flash-sale order flow.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  ASYNC ORDER CREATION FLOW (Layer 4)                             │
 * │                                                                  │
 * │  1. [gRPC] Call InventoryService.deductStock                     │
 * │     → Multi-level cache: JVM → Redis Lua → DB fallback           │
 * │     → If failed: return "out of stock" immediately               │
 * │                                                                  │
 * │  2. [DB TX] Within a single transaction:                         │
 * │     a. Insert Order (status = PENDING_PAYMENT)                   │
 * │     b. Insert LocalMessage (state = NEW, topic = ORDER_CREATED)  │
 * │     → The stock_log is written by inventory service upon         │
 * │       receiving the ORDER_CREATED event                          │
 * │                                                                  │
 * │  3. [After TX commit] Async send Kafka message                   │
 * │     → TransactionSynchronization.afterCommit() ensures we        │
 * │       only send after data is safely persisted                   │
 * │     → If send fails, the outbox scanner will retry               │
 * │                                                                  │
 * │  WHY STOCK IS LOCKED BEFORE PAYMENT:                             │
 * │  If we wait for payment before locking stock, two users could    │
 * │  both see stock=1, both start payment, and both succeed →        │
 * │  overselling. By locking (Redis DECR) first, we guarantee that   │
 * │  only one user can claim the last unit. Payment then confirms    │
 * │  or releases the lock.                                           │
 * │                                                                  │
 * │  WHY PAYMENT IS ASYNC:                                           │
 * │  Payment gateways (Stripe) have ~200ms latency and               │
 * │  rate limits. If we made payment synchronous during a 10k QPS    │
 * │  flash sale, the payment gateway would be the bottleneck.        │
 * │  Instead, we return "order created" immediately and process      │
 * │  payment asynchronously via Kafka events.                        │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GrpcClient("flash-sale-inventory")
    private InventoryServiceRPCGrpc.InventoryServiceRPCBlockingStub inventoryStub;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private LocalMessageRepository localMessageRepository;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Create a flash-sale order.
     * 
     * This method orchestrates the complete order creation flow:
     * 1. Redis stock pre-deduction (via gRPC to inventory service)
     * 2. Transactional order + outbox message persistence
     * 3. Async Kafka message dispatch
     *
     * @param productId the product to order
     * @return result map with orderId and status
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long productId) {
        log.info("[OrderService] createOrder start, productId={}", productId);

        // ═══════════════════════════════════════════════════════════
        // STEP 1: Redis stock pre-deduction via gRPC
        // This hits the multi-level cache in InventoryService:
        //   JVM cache → Redis Lua → DB fallback
        // ═══════════════════════════════════════════════════════════
        DeductStockRequest request = DeductStockRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(1)
                .build();

        DeductStockResponse response;
        try {
            response = inventoryStub.deductStock(request);
        } catch (Exception e) {
            log.error("[OrderService] gRPC call failed", e);
            return errorResult("Inventory service unavailable. Please try again.");
        }

        if (!response.getSuccess()) {
            log.warn("[OrderService] Out of stock: productId={}, msg={}", productId, response.getMessage());
            return errorResult("Out of stock: " + response.getMessage());
        }

        // ═══════════════════════════════════════════════════════════
        // STEP 2: Within a single DB transaction, persist:
        //   a. Order (status = PENDING_PAYMENT)
        //   b. LocalMessage (outbox entry for ORDER_CREATED event)
        //
        // WHY SAME TRANSACTION:
        // If order insert succeeds but message insert fails (or vice
        // versa), we'd have inconsistency. The outbox pattern ensures
        // both succeed or both roll back atomically.
        // ═══════════════════════════════════════════════════════════

        // 2a. Insert Order
        Order order = new Order();
        order.setProductId(productId);
        order.setUserId(1L); // Simplified: hardcoded user
        order.setStatus(Order.STATUS_PENDING_PAYMENT);
        Order savedOrder = orderRepository.save(order);
        log.info("[OrderService] Order saved: orderId={}, status=PENDING_PAYMENT", savedOrder.getId());

        // 2b. Insert LocalMessage (outbox)
        try {
            Map<String, Object> messageBody = new LinkedHashMap<>();
            messageBody.put("orderId", savedOrder.getId());
            messageBody.put("productId", productId);
            messageBody.put("quantity", 1);
            messageBody.put("userId", 1L);
            String jsonContent = objectMapper.writeValueAsString(messageBody);

            LocalMessage message = new LocalMessage();
            message.setBusinessKey(String.valueOf(savedOrder.getId()));
            message.setTopic("ORDER_CREATED");
            message.setContent(jsonContent);
            message.setNextRetryTime(LocalDateTime.now()); // eligible for immediate send
            LocalMessage savedMessage = localMessageRepository.save(message);
            log.info("[OrderService] LocalMessage saved: msgId={}, topic=ORDER_CREATED", savedMessage.getId());

            // ═══════════════════════════════════════════════════════
            // STEP 3: After TX commits, send Kafka message
            // We use TransactionSynchronization to ensure the message
            // is only sent after the DB commit succeeds. If the TX
            // rolls back, afterCommit() never fires → no phantom msg.
            // ═══════════════════════════════════════════════════════
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("[OrderService] TX committed, dispatching Kafka for msgId={}", savedMessage.getId());
                    sendKafkaMessage(savedMessage);
                }
            });

        } catch (Exception e) {
            log.error("[OrderService] Failed to create outbox message", e);
            throw new RuntimeException("Failed to create order message", e);
        }

        log.info("[OrderService] createOrder END, orderId={}", savedOrder.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderId", savedOrder.getId());
        result.put("status", "PENDING_PAYMENT");
        result.put("message", "Order created! Awaiting payment.");
        return result;
    }

    /**
     * Send a Kafka message and update the outbox state.
     * Called both by afterCommit() and by the retry scanner.
     */
    public void sendKafkaMessage(LocalMessage message) {
        log.info("[Kafka] Sending: msgId={}, topic={}", message.getId(), message.getTopic());
        try {
            kafkaTemplate.send(message.getTopic(), message.getContent())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("[Kafka] Send success: msgId={}", message.getId());
                            message.setState(LocalMessage.STATE_SENT);
                            localMessageRepository.save(message);
                        } else {
                            log.error("[Kafka] Send failed: msgId={}", message.getId(), ex);
                            // Don't update state here; the retry scanner will pick it up
                        }
                    });
        } catch (Exception e) {
            log.error("[Kafka] Exception while sending: msgId={}", message.getId(), e);
        }
    }

    private Map<String, Object> errorResult(String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", msg);
        return result;
    }
}
