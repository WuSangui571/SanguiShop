ALTER TABLE oms_order
    ADD COLUMN receipt_request_id VARCHAR(64) NULL AFTER shipment_trace_id,
    ADD COLUMN receipt_trace_id VARCHAR(64) NULL AFTER receipt_request_id,
    ADD COLUMN completed_at DATETIME NULL AFTER receipt_trace_id;

CREATE INDEX idx_oms_order_shop_completed_created ON oms_order (shop_id, status, completed_at);
