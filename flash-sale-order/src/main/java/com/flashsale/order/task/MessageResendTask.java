package com.flashsale.order.task;

import com.flashsale.order.entity.LocalMessage;
import com.flashsale.order.repository.LocalMessageRepository;
import com.flashsale.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox scanner / message resend task.
 *
 * Periodically scans the local_message table for messages that were
 * not successfully sent to Kafka (state = NEW and created > 1 minute ago).
 *
 * WHY 1 MINUTE THRESHOLD:
 * The normal path sends Kafka messages via afterCommit() immediately.
 * If a message is still NEW after 1 minute, it means the afterCommit
 * failed (e.g., app crash, Kafka broker down). The 1-minute buffer
 * prevents the scanner from racing with the normal afterCommit path.
 *
 * RETRY STRATEGY:
 * - Max 5 retries before marking as FAIL
 * - Exponential backoff: 1min, 2min, 4min, 8min, 16min
 * - FAIL state requires manual intervention (alert in production)
 */
@Component
public class MessageResendTask {

    private static final Logger log = LoggerFactory.getLogger(MessageResendTask.class);
    private static final int MAX_RETRIES = 5;

    @Autowired
    private LocalMessageRepository localMessageRepository;

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void resendFailedMessages() {
        // Find messages created > 1 minute ago that are still NEW
        LocalDateTime timeThreshold = LocalDateTime.now().minusMinutes(1);
        List<LocalMessage> stuckMessages = localMessageRepository
                .findByStateAndCreateTimeBefore(LocalMessage.STATE_NEW, timeThreshold);

        if (stuckMessages.isEmpty()) {
            return;
        }

        log.info("[OutboxScanner] Found {} stuck messages. Processing...", stuckMessages.size());

        for (LocalMessage msg : stuckMessages) {
            // Exceeded max retries → mark as FAIL for manual intervention
            if (msg.getRetryCount() >= MAX_RETRIES) {
                msg.setState(LocalMessage.STATE_FAIL);
                localMessageRepository.save(msg);
                log.error("[OutboxScanner] Message {} exceeded max retries. Marked as FAIL. " +
                          "Manual intervention required.", msg.getId());
                continue;
            }

            // Increment retry count and set next retry time (exponential backoff)
            msg.setRetryCount(msg.getRetryCount() + 1);
            int backoffMinutes = (int) Math.pow(2, msg.getRetryCount() - 1);
            msg.setNextRetryTime(LocalDateTime.now().plusMinutes(backoffMinutes));
            localMessageRepository.save(msg);

            log.info("[OutboxScanner] Resending message {}, retry #{}, next retry in {} min",
                    msg.getId(), msg.getRetryCount(), backoffMinutes);

            // Resend
            orderService.sendKafkaMessage(msg);
        }
    }
}
