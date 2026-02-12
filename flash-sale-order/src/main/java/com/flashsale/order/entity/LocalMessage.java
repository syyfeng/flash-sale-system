package com.flashsale.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Transactional Outbox entity.
 *
 * WHY OUTBOX PATTERN:
 * The classic dual-write problem: we need to both persist business data
 * (order, stock_log) and send a Kafka message. If we commit the DB TX
 * then Kafka send fails, the message is lost. If we send Kafka first
 * then the DB TX rolls back, we have a phantom message.
 *
 * Solution: Write the message to a `local_message` table within the
 * SAME DB transaction as the business data. A background scanner then
 * reads unsent messages and publishes them to Kafka. If the scanner
 * fails, it retries. This guarantees at-least-once delivery without
 * requiring distributed transactions (2PC).
 *
 * state:
 *   0 = NEW   (written in same TX, not yet sent to Kafka)
 *   1 = SENT  (Kafka acknowledged receipt)
 *   2 = FAIL  (exceeded max retries, needs manual intervention)
 */
@Entity
@Table(name = "local_message")
public class LocalMessage {

    public static final int STATE_NEW = 0;
    public static final int STATE_SENT = 1;
    public static final int STATE_FAIL = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business correlation key (e.g., orderId) */
    @Column(name = "business_key")
    private String businessKey;

    /** Kafka topic name */
    private String topic;

    /** Message body (JSON) */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 0=NEW, 1=SENT, 2=FAIL */
    private Integer state;

    private Integer retryCount;

    /** Next scheduled retry time */
    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        if (this.state == null) this.state = STATE_NEW;
        if (this.retryCount == null) this.retryCount = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getState() { return state; }
    public void setState(Integer state) { this.state = state; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
