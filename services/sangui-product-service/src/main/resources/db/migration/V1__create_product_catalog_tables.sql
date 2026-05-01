CREATE TABLE pms_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    product_name VARCHAR(128) NOT NULL,
    product_description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pms_product_shop_id_id (shop_id, id),
    KEY idx_pms_product_shop_status (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pms_sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL DEFAULT 1,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(128) NOT NULL,
    sale_price_cent BIGINT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pms_sku_shop_code (shop_id, sku_code),
    KEY idx_pms_sku_shop_product (shop_id, product_id),
    CONSTRAINT fk_pms_sku_product FOREIGN KEY (product_id) REFERENCES pms_product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
