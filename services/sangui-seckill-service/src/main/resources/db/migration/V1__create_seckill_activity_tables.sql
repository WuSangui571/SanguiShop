CREATE TABLE sk_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    activity_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_sk_activity_shop_request ON sk_activity (shop_id, request_id, deleted);
CREATE INDEX idx_sk_activity_shop_status_created ON sk_activity (shop_id, status, created_at, id);

CREATE TABLE sk_activity_sku (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    sku_id BIGINT NOT NULL,
    sku_code VARCHAR(100) NOT NULL,
    sku_name VARCHAR(200) NOT NULL,
    price_cent BIGINT NOT NULL,
    seckill_price_cent BIGINT NOT NULL,
    available_stock BIGINT NOT NULL,
    activity_stock BIGINT NOT NULL,
    sold_count BIGINT NOT NULL DEFAULT 0,
    request_id VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_sk_activity_sku_binding ON sk_activity_sku (shop_id, activity_id, sku_id);
CREATE INDEX idx_sk_activity_sku_shop_activity ON sk_activity_sku (shop_id, activity_id, deleted);
CREATE INDEX idx_sk_activity_sku_request ON sk_activity_sku (shop_id, activity_id, request_id);

CREATE TABLE sk_activity_status_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    activity_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    target_status VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_sk_activity_status_request ON sk_activity_status_request (shop_id, activity_id, request_id);
