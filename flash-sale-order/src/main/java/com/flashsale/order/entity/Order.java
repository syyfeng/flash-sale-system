package com.flashsale.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Order entity with full lifecycle states.
 *
 * Status transitions:
 *   0 (CREATED) → internal, immediately transitions to PENDING_PAYMENT
 *   1 (PENDING_PAYMENT) → stock locked in Redis, awaiting payment
 *   2 (PAID) → payment confirmed, DB stock deducted
 *   3 (CANCELED) → payment failed/timeout, Redis stock rolled back
 *
 * WHY PENDING_PAYMENT instead of direct PAID:
 * Flash sales have extremely high concurrency. If we waited for payment
 * before releasing the response, the user would experience long delays.
 * Instead, we reserve stock (Redis DECR) and let the user pay asynchronously.
 * This decouples the "stock reservation" from "payment confirmation",
 * allowing the system to handle orders at Redis speed (~100k QPS)
 * rather than payment-gateway speed (~100 QPS).
 */
@Entity
@Table(name = "orders")
public class Order {

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_PENDING_PAYMENT = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_CANCELED = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 0=CREATED, 1=PENDING_PAYMENT, 2=PAID, 3=CANCELED
     */
    @Column(nullable = false)
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
