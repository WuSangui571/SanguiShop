ALTER TABLE pay_payment_order
    ADD COLUMN reservation_no VARCHAR(64) NOT NULL AFTER user_id;
