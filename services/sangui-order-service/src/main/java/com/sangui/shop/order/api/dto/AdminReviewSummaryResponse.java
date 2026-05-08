package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminReviewSummaryResponse(
        Long reviewId,
        Long orderId,
        String orderNo,
        Long productId,
        Long skuId,
        String skuName,
        Integer rating,
        String content,
        int imageCount,
        List<String> imageUrls,
        String maskedUserId,
        String visibilityStatus,
        String visibilityReason,
        String visibilityRequestId,
        String visibilityOperator,
        String visibilityTraceId,
        OffsetDateTime visibilityUpdatedAt,
        String replyContent,
        String replyVisibilityStatus,
        String replyRequestId,
        String replyOperator,
        String replyTraceId,
        OffsetDateTime replyUpdatedAt,
        OffsetDateTime createdAt
) {
}
