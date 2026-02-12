package com.flashsale.order.controller;

import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.LocalMessageRepository;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.service.OrderService;
import com.flashsale.order.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for order management.
 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LocalMessageRepository localMessageRepository;

    @Autowired
    private PaymentService paymentService;

    // ─── Core Flash Sale Endpoint ─────────────────────────────────

    /**
     * Create a flash-sale order.
     * This is the main entry point for the "Buy Now" button.
     */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestParam("productId") Long productId) {
        return orderService.createOrder(productId);
    }

    // ─── Order Query Endpoints ────────────────────────────────────

    /**
     * List all orders (for admin dashboard).
     */
    @GetMapping("/list")
    public List<Order> listOrders() {
        return orderRepository.findAll();
    }

    /**
     * Get order stats (for admin dashboard).
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", orderRepository.count());
        stats.put("pendingPayment", orderRepository.countByStatus(Order.STATUS_PENDING_PAYMENT));
        stats.put("paid", orderRepository.countByStatus(Order.STATUS_PAID));
        stats.put("canceled", orderRepository.countByStatus(Order.STATUS_CANCELED));
        return stats;
    }

    // ─── Payment Config (Admin) ───────────────────────────────────

    /**
     * Configure the mock payment service behavior.
     */
    @PostMapping("/payment/config")
    public Map<String, String> configurePayment(@RequestBody Map<String, Object> body) {
        String result = (String) body.getOrDefault("result", "SUCCESS");
        int delayMs = body.get("delayMs") != null
                ? Integer.parseInt(body.get("delayMs").toString()) : 50;

        paymentService.setConfiguredResult(PaymentService.PaymentResult.valueOf(result.toUpperCase()));
        paymentService.setSimulatedDelayMs(delayMs);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Payment config updated: result=" + result + ", delay=" + delayMs + "ms");
        return response;
    }

    /**
     * Get current payment configuration.
     */
    @GetMapping("/payment/config")
    public Map<String, Object> getPaymentConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("result", paymentService.getConfiguredResult().name());
        config.put("delayMs", paymentService.getSimulatedDelayMs());
        return config;
    }

    // ─── System Reset (Debug) ─────────────────────────────────────

    /**
     * Reset orders and local_messages (used with inventory reset).
     */
    @PostMapping("/reset")
    public Map<String, String> resetOrders() {
        long orderCount = orderRepository.count();
        long msgCount = localMessageRepository.count();

        orderRepository.deleteAll();
        localMessageRepository.deleteAll();

        log.info("[Reset] Deleted {} orders and {} local_messages", orderCount, msgCount);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "Orders reset complete. Deleted " + orderCount +
                   " orders and " + msgCount + " messages.");
        return result;
    }
}
