package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record OrderRecord(
        Long id,
        Long shopId,
        String userId,
        String orderNo,
        String requestId,
        String reservationNo,
        OrderStatus status,
        Long totalAmountCent,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String lastCompensationResult,
        String lastCompensationErrorCode,
        String lastCompensationReason,
        String lastCompensationTraceId,
        String lastCompensationTrigger,
        String lastCompensationOperator,
        LocalDateTime lastCompensatedAt,
        String fulfillmentStatus,
        String carrier,
        String trackingNo,
        LocalDateTime shippedAt,
        String shipmentRequestId,
        String shipmentTraceId
) {
    public OrderRecord(
            Long id,
            Long shopId,
            String userId,
            String orderNo,
            String requestId,
            String reservationNo,
            OrderStatus status,
            Long totalAmountCent,
            String traceId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String lastCompensationResult,
            String lastCompensationErrorCode,
            String lastCompensationReason,
            String lastCompensationTraceId,
            String lastCompensationTrigger,
            String lastCompensationOperator,
            LocalDateTime lastCompensatedAt
    ) {
        this(
                id,
                shopId,
                userId,
                orderNo,
                requestId,
                reservationNo,
                status,
                totalAmountCent,
                traceId,
                createdAt,
                updatedAt,
                lastCompensationResult,
                lastCompensationErrorCode,
                lastCompensationReason,
                lastCompensationTraceId,
                lastCompensationTrigger,
                lastCompensationOperator,
                lastCompensatedAt,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
