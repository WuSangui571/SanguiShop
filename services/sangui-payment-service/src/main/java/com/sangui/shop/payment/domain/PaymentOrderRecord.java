package com.sangui.shop.payment.domain;

import java.time.LocalDateTime;

public record PaymentOrderRecord(
        Long id,
        Long shopId,
        Long orderId,
        String orderNo,
        String userId,
        String reservationNo,
        String paymentNo,
        String channel,
        Long amountCent,
        PaymentStatus status,
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
    public PaymentOrderRecord withStatus(PaymentStatus nextStatus) {
        return new PaymentOrderRecord(
                id,
                shopId,
                orderId,
                orderNo,
                userId,
                reservationNo,
                paymentNo,
                channel,
                amountCent,
                nextStatus,
                traceId,
                createdAt,
                updatedAt,
                lastCompensationResult,
                lastCompensationErrorCode,
                lastCompensationReason,
                lastCompensationTraceId,
                lastCompensationTrigger,
                lastCompensationOperator,
                lastCompensatedAt
        );
    }
}
