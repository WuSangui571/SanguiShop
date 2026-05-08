package com.sangui.shop.order.domain;

import java.time.LocalDateTime;
import java.util.List;

public record AdminReviewListItem(
        Long reviewId,
        Long shopId,
        Long orderId,
        String orderNo,
        Long productId,
        Long skuId,
        String skuName,
        String userId,
        Integer rating,
        String content,
        List<String> imageUrls,
        ReviewVisibilityStatus visibilityStatus,
        String visibilityReason,
        String visibilityRequestId,
        String visibilityOperator,
        String visibilityTraceId,
        LocalDateTime visibilityUpdatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
