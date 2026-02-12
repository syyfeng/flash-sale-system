package com.flashsale.inventory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stock flow log — used for idempotency and compensation.
 *
 * Each order gets exactly one stock_log entry. The status tracks
 * the lifecycle of the stock reservation:
 *   0 = LOCKED       → Redis stock pre-deducted, DB stock not yet touched
 *   1 = DEDUCTED     → Payment succeeded, DB stock deducted (final state)
 *   2 = ROLLED_BACK  → Payment failed/timeout, Redis stock restored (final state)
 *
 * The unique constraint on order_id ensures idempotency:
 * processing the same order twice will not create duplicate stock movements.
 */
@Entity
@Table(name = "stock_log")
public class StockLog {

    public static final int STATUS_LOCKED = 0;
    public static final int STATUS_DEDUCTED = 1;
    public static final int STATUS_ROLLED_BACK = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * 0=LOCKED, 1=DEDUCTED, 2=ROLLED_BACK
     */
    @Column(nullable = false)
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        if (this.status == null) {
            this.status = STATUS_LOCKED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
