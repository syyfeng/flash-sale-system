package com.flashsale.inventory.repository;

import com.flashsale.inventory.entity.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {

    Optional<InventoryStock> findByProductId(Long productId);

    /**
     * Atomic DB stock deduction with optimistic check (stock >= quantity).
     * This avoids distributed locks — the WHERE clause acts as a CAS guard.
     *
     * WHY NOT USE DISTRIBUTED LOCK:
     * A distributed lock (e.g., Redisson) serializes all requests, capping
     * throughput at ~1k QPS. The WHERE-clause approach allows the DB engine
     * to handle concurrency natively via row-level locks, which is faster
     * and simpler for single-row updates.
     *
     * @return number of rows affected (1 = success, 0 = insufficient stock)
     */
    @Modifying
    @Query("UPDATE InventoryStock s SET s.stock = s.stock - :qty, s.updateTime = CURRENT_TIMESTAMP " +
           "WHERE s.productId = :productId AND s.stock >= :qty")
    int deductStock(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Restore stock after payment failure / timeout.
     * This is the compensation side of the saga.
     */
    @Modifying
    @Query("UPDATE InventoryStock s SET s.stock = s.stock + :qty, s.updateTime = CURRENT_TIMESTAMP " +
           "WHERE s.productId = :productId")
    int restoreStock(@Param("productId") Long productId, @Param("qty") int qty);
}
