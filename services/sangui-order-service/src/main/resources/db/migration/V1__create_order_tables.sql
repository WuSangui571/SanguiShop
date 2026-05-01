CREATE TABLE oms_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    order_no VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'created',
    total_amount_cent BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_oms_order_shop_order_no (shop_id, order_no),
    UNIQUE KEY uk_oms_order_shop_user_request (shop_id, user_id, request_id),
    KEY idx_oms_order_shop_user_id (shop_id, user_id, id),
    KEY idx_oms_order_shop_status (shop_id, status)
);

CREATE TABLE oms_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    sku_name VARCHAR(128) NOT NULL,
    price_cent BIGINT NOT NULL,
    quantity INT NOT NULL,
    line_amount_cent BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_oms_order_item_order FOREIGN KEY (order_id) REFERENCES oms_order (id),
    KEY idx_oms_order_item_shop_order (shop_id, order_id),
    KEY idx_oms_order_item_shop_sku (shop_id, sku_id)
);
