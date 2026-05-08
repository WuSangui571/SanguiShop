package com.sangui.shop.product.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProductReviewItemResponse(
        Long reviewId,
        Integer rating,
        String content,
        List<String> imageUrls,
        OffsetDateTime createdAt,
        String maskedUserId,
        String skuName
) {
}
