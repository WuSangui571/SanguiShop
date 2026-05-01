package com.sangui.shop.payment.domain;

public record PaymentCreateDraft(
        Long shopId,
        Long orderId,
        String orderNo,
        String userId,
        String reservationNo,
        String paymentNo,
        String channel,
        Long amountCent,
        String traceId
) {
}
