package com.flashsale.order.repository;

import com.flashsale.order.entity.LocalMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LocalMessageRepository extends JpaRepository<LocalMessage, Long> {

    /**
     * Find messages eligible for retry:
     * - State is NEW (0) or FAIL (2, but under max retries)
     * - next_retry_time <= now (or null for new messages)
     */
    List<LocalMessage> findByStateAndCreateTimeBefore(Integer state, LocalDateTime time);

    /**
     * Find all messages in NEW state that need sending.
     * Used by the outbox scanner.
     */
    List<LocalMessage> findByStateInAndNextRetryTimeLessThanEqual(
            List<Integer> states, LocalDateTime time);
}
