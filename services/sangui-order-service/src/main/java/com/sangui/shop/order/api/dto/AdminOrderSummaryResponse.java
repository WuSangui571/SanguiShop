package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;

public record AdminOrderSummaryResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String status,
        Long totalAmountCent,
        String paymentNo,
        int itemCount,
        String traceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
