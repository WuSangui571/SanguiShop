package com.sangui.shop.order.domain;

import java.time.LocalDateTime;
import java.util.List;

public record OrderReviewRecord(
        Long id,
        Long shopId,
        Long orderId,
        String orderNo,
        String userId,
        Integer rating,
        String content,
        List<String> imageUrls,
        String requestId,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
