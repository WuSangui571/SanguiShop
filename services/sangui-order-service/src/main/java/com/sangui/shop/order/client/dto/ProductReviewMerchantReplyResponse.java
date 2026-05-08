package com.sangui.shop.order.client.dto;

import java.time.OffsetDateTime;

public record ProductReviewMerchantReplyResponse(
        String content,
        OffsetDateTime repliedAt
) {
}
