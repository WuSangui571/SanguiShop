ALTER TABLE oms_order
    ADD COLUMN fulfillment_status VARCHAR(32) NULL AFTER total_amount_cent,
    ADD COLUMN carrier VARCHAR(64) NULL AFTER fulfillment_status,
    ADD COLUMN tracking_no VARCHAR(128) NULL AFTER carrier,
    ADD COLUMN shipped_at DATETIME NULL AFTER tracking_no,
    ADD COLUMN shipment_request_id VARCHAR(64) NULL AFTER shipped_at,
    ADD COLUMN shipment_trace_id VARCHAR(64) NULL AFTER shipment_request_id;

CREATE INDEX idx_oms_order_shop_fulfillment_created ON oms_order (shop_id, fulfillment_status, created_at);
