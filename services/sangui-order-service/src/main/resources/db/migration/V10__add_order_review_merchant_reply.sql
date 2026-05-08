ALTER TABLE oms_order_review
    ADD COLUMN reply_content VARCHAR(300) NULL AFTER visibility_updated_at,
    ADD COLUMN reply_visibility_status VARCHAR(16) NOT NULL DEFAULT 'visible' AFTER reply_content,
    ADD COLUMN reply_request_id VARCHAR(64) NULL AFTER reply_visibility_status,
    ADD COLUMN reply_operator VARCHAR(64) NULL AFTER reply_request_id,
    ADD COLUMN reply_trace_id VARCHAR(64) NULL AFTER reply_operator,
    ADD COLUMN reply_updated_at DATETIME NULL AFTER reply_trace_id;

CREATE INDEX idx_oms_order_review_shop_reply_visibility_updated
    ON oms_order_review (shop_id, reply_visibility_status, reply_updated_at);
