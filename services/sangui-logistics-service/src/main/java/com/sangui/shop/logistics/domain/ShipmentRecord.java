package com.sangui.shop.logistics.domain;

import java.time.LocalDateTime;

public record ShipmentRecord(
        Long id,
        Long shopId,
        Long orderId,
        String orderNo,
        String userId,
        String carrier,
        String trackingNo,
        ShipmentStatus status,
        String requestId,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
