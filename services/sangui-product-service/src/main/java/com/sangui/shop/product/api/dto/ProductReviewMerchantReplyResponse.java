package com.sangui.shop.product.api.dto;

import java.time.OffsetDateTime;

public record ProductReviewMerchantReplyResponse(
        String content,
        OffsetDateTime repliedAt
) {
}
