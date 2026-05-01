CREATE TABLE pay_payment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    amount_cent BIGINT NOT NULL,
    trace_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'created',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pay_payment_order_shop_payment_no (shop_id, payment_no),
    KEY idx_pay_payment_order_shop_order_id (shop_id, order_id),
    KEY idx_pay_payment_order_shop_user_id (shop_id, user_id, id),
    KEY idx_pay_payment_order_shop_status (shop_id, status)
);

CREATE TABLE pay_callback_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    payment_no VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    channel_trade_no VARCHAR(64) NULL,
    callback_type VARCHAR(32) NOT NULL DEFAULT 'payment',
    payload_json TEXT NULL,
    process_status VARCHAR(32) NOT NULL DEFAULT 'received',
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pay_callback_log_channel_trade_no (channel, channel_trade_no),
    KEY idx_pay_callback_log_shop_payment_no (shop_id, payment_no)
);
