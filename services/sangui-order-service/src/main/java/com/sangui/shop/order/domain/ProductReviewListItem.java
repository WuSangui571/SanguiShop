package com.sangui.shop.order.domain;

import java.time.LocalDateTime;
import java.util.List;

public record ProductReviewListItem(
        Long reviewId,
        Integer rating,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt,
        String userId,
        String skuName
) {
}
