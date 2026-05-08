ALTER TABLE oms_order_review
    ADD COLUMN visibility_status VARCHAR(16) NOT NULL DEFAULT 'visible' AFTER trace_id,
    ADD COLUMN visibility_reason VARCHAR(200) NULL AFTER visibility_status,
    ADD COLUMN visibility_request_id VARCHAR(64) NULL AFTER visibility_reason,
    ADD COLUMN visibility_operator VARCHAR(64) NULL AFTER visibility_request_id,
    ADD COLUMN visibility_trace_id VARCHAR(64) NULL AFTER visibility_operator,
    ADD COLUMN visibility_updated_at DATETIME NULL AFTER visibility_trace_id,
    ADD KEY idx_oms_order_review_shop_visibility_created (shop_id, visibility_status, created_at);
