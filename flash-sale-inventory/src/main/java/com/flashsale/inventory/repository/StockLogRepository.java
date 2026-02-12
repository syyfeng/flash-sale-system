package com.flashsale.inventory.repository;

import com.flashsale.inventory.entity.StockLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockLogRepository extends JpaRepository<StockLog, Long> {

    /**
     * Find stock log by orderId — used for idempotency checks.
     * Before processing any stock operation, we check if a log
     * already exists for this order to prevent duplicate processing.
     */
    Optional<StockLog> findByOrderId(Long orderId);
}
