ALTER TABLE pay_payment_order
    ADD COLUMN last_compensation_result VARCHAR(32) NULL AFTER status,
    ADD COLUMN last_compensation_error_code VARCHAR(64) NULL AFTER last_compensation_result,
    ADD COLUMN last_compensation_reason VARCHAR(255) NULL AFTER last_compensation_error_code,
    ADD COLUMN last_compensation_trace_id VARCHAR(64) NULL AFTER last_compensation_reason,
    ADD COLUMN last_compensation_trigger VARCHAR(32) NULL AFTER last_compensation_trace_id,
    ADD COLUMN last_compensated_at DATETIME NULL AFTER last_compensation_trigger;
