package com.sangui.shop.payment.domain;

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
        String traceId
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
                traceId
        );
    }
}
