-- ============================================================
-- Flash Sale System - Enterprise DDL
-- MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS flash_sale
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE flash_sale;

-- -----------------------------------------------------------
-- 1. users  (simplified for demo; extend as needed)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL DEFAULT '',
    phone       VARCHAR(20)  NOT NULL DEFAULT '',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User accounts';

-- -----------------------------------------------------------
-- 2. products  (catalog – relatively low-frequency writes)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL DEFAULT '',
    price       INT          NOT NULL DEFAULT 0  COMMENT 'Price in cents',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Product catalog';

-- -----------------------------------------------------------
-- 3. inventory_stock  (separated for high-frequency IO)
--    WHY SEPARATED: The products table is read-heavy (name,
--    price, description). Stock is write-heavy under flash
--    sale. Separating them avoids row-lock contention between
--    reads and writes on the same table.
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory_stock (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT  NOT NULL,
    stock       INT     NOT NULL DEFAULT 0 COMMENT 'Available stock quantity',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Inventory stock (hot table, separated from products)';

-- -----------------------------------------------------------
-- 4. flash_sales  (flash-sale event configuration)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS flash_sales (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT   NOT NULL,
    sale_price  INT      NOT NULL DEFAULT 0 COMMENT 'Flash sale price in cents',
    start_time  DATETIME NOT NULL,
    end_time    DATETIME NOT NULL,
    status      TINYINT  NOT NULL DEFAULT 0 COMMENT '0=PENDING 1=ACTIVE 2=ENDED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flash sale events';

-- -----------------------------------------------------------
-- 5. orders  (full lifecycle states)
--    status:
--      0 = CREATED          (order record inserted)
--      1 = PENDING_PAYMENT  (stock locked, awaiting payment)
--      2 = PAID             (payment success, stock deducted)
--      3 = CANCELED         (payment failed/timeout, stock rolled back)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL DEFAULT 0,
    status      TINYINT  NOT NULL DEFAULT 0 COMMENT '0=CREATED 1=PENDING_PAYMENT 2=PAID 3=CANCELED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_product_status (product_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Order table with full lifecycle';

-- -----------------------------------------------------------
-- 6. stock_log  (inventory flow control)
--    Used for idempotency and compensation.
--    status:
--      0 = LOCKED       (stock reserved in Redis, DB not yet deducted)
--      1 = DEDUCTED     (payment success → DB stock deducted)
--      2 = ROLLED_BACK  (payment failed → Redis stock restored)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT   NOT NULL,
    product_id  BIGINT   NOT NULL,
    quantity    INT      NOT NULL DEFAULT 1,
    status      TINYINT  NOT NULL DEFAULT 0 COMMENT '0=LOCKED 1=DEDUCTED 2=ROLLED_BACK',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_id (order_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stock flow log for idempotency and compensation';

-- -----------------------------------------------------------
-- 7. local_message  (transactional outbox)
--    Guarantees reliable MQ delivery via outbox pattern.
--    state:
--      0 = NEW   (written in same TX as business data)
--      1 = SENT  (Kafka ack received)
--      2 = FAIL  (exceeded max retries, needs manual intervention)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS local_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_key    VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'e.g. orderId for correlation',
    topic           VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Kafka topic name',
    content         TEXT         NOT NULL             COMMENT 'Message body (JSON)',
    state           TINYINT      NOT NULL DEFAULT 0   COMMENT '0=NEW 1=SENT 2=FAIL',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME     NULL     DEFAULT NULL COMMENT 'Next scheduled retry',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_state_retry (state, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional outbox for reliable Kafka delivery';
