ALTER TABLE pms_sku
    ADD COLUMN available_stock BIGINT NOT NULL DEFAULT 0 AFTER sale_price_cent,
    ADD COLUMN reserved_stock BIGINT NOT NULL DEFAULT 0 AFTER available_stock;

CREATE TABLE pms_inventory_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    reservation_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(128) NOT NULL,
    price_cent BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'reserved',
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_inventory_reservation_shop_no_sku (shop_id, reservation_no, sku_id),
    KEY idx_pms_inventory_reservation_shop_no (shop_id, reservation_no),
    KEY idx_pms_inventory_reservation_shop_status (shop_id, status),
    KEY idx_pms_inventory_reservation_shop_sku (shop_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
