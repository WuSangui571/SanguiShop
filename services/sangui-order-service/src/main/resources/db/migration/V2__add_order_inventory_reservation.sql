ALTER TABLE oms_order
    ADD COLUMN reservation_no VARCHAR(64) NOT NULL AFTER request_id;

CREATE UNIQUE INDEX uk_oms_order_shop_reservation_no ON oms_order (shop_id, reservation_no);
