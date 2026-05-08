package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderReviewResponse(
        Long orderReviewId,
        Long shopId,
        Long orderId,
        String orderNo,
        String userId,
        Integer rating,
        String content,
        List<String> imageUrls,
        String requestId,
        String traceId,
        OffsetDateTime createdAt
) {
}
