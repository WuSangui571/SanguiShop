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
        OffsetDateTime updatedAt,
        String fulfillmentStatus,
        String carrier,
        String trackingNo,
        OffsetDateTime shippedAt,
        OffsetDateTime completedAt
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
        this(orderId, orderNo, shopId, userId, requestId, status, totalAmountCent, items, null, null, null, null, null, null, null);
    }

    public OrderResponse(
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
        this(orderId, orderNo, shopId, userId, requestId, status, totalAmountCent, items, createdAt, updatedAt, null, null, null, null, null);
    }

    public OrderResponse(
            Long orderId,
            String orderNo,
            Long shopId,
            String userId,
            String requestId,
            String status,
            Long totalAmountCent,
            List<OrderItemResponse> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String fulfillmentStatus,
            String carrier,
            String trackingNo,
            OffsetDateTime shippedAt
    ) {
        this(orderId, orderNo, shopId, userId, requestId, status, totalAmountCent, items, createdAt, updatedAt, fulfillmentStatus, carrier, trackingNo, shippedAt, null);
    }
}
