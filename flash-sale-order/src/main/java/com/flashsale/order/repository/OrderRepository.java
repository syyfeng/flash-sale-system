package com.flashsale.order.repository;

import com.flashsale.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Update order status with optimistic check on current status.
     * Prevents illegal transitions (e.g., CANCELED → PAID).
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updateTime = CURRENT_TIMESTAMP " +
           "WHERE o.id = :orderId AND o.status = :currentStatus")
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("currentStatus") int currentStatus,
                     @Param("newStatus") int newStatus);

    /** Find orders by status (for admin dashboard) */
    List<Order> findByStatus(Integer status);

    /** Count orders by status (for admin dashboard) */
    long countByStatus(Integer status);
}
