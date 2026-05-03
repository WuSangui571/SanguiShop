ALTER TABLE pay_payment_order
    ADD COLUMN last_compensation_operator VARCHAR(64) NULL AFTER last_compensation_trigger;

CREATE TABLE pay_payment_compensation_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    reservation_no VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    reason VARCHAR(255) NULL,
    trace_id VARCHAR(64) NULL,
    trigger_type VARCHAR(32) NOT NULL,
    operator VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pay_payment_comp_attempt_shop_payment_created
    ON pay_payment_compensation_attempt (shop_id, payment_id, created_at);
