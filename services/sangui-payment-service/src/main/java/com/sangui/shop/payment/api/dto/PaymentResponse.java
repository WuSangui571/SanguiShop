package com.sangui.shop.payment.api.dto;

public record PaymentResponse(
        Long paymentId,
        String paymentNo,
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String channel,
        String status,
        Long amountCent
) {
}
