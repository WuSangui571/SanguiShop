package com.sangui.shop.order.client.dto;

import java.time.OffsetDateTime;

public record FulfillmentOrderResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String status,
        String fulfillmentStatus,
        Long totalAmountCent,
        String carrier,
        String trackingNo,
        OffsetDateTime shippedAt,
        String traceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
