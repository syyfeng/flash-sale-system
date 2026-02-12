package com.flashsale.inventory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Separated inventory stock table — the "hot" table under flash-sale load.
 *
 * WHY SEPARATED FROM PRODUCTS:
 * During a flash sale, stock updates can reach 10k+ QPS. If stock were a
 * column in the `products` table, every stock write would lock the product
 * row, blocking concurrent reads for product name/price. By separating
 * stock into its own table, we isolate the write-hot path and eliminate
 * contention with read-heavy catalog queries.
 */
@Entity
@Table(name = "inventory_stock")
public class InventoryStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private Integer stock;

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
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
