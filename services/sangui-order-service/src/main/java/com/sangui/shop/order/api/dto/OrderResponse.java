package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String requestId,
        String status,
        Long totalAmountCent,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public OrderResponse(
            Long orderId,
            String orderNo,
            Long shopId,
            String userId,
            String requestId,
            String status,
            Long totalAmountCent,
            List<OrderItemResponse> items
    ) {
        this(orderId, orderNo, shopId, userId, requestId, status, totalAmountCent, items, null, null);
    }
}
