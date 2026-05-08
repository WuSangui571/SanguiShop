package com.sangui.shop.product.client.dto;

import java.time.OffsetDateTime;

public record ProductReviewMerchantReplyResponse(
        String content,
        OffsetDateTime repliedAt
) {
}
