package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminOrderDetailResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String requestId,
        String reservationNo,
        String paymentNo,
        String status,
        Long totalAmountCent,
        String traceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemResponse> items,
        List<AdminOrderStatusTimelineResponse> statusTimeline
) {
}
