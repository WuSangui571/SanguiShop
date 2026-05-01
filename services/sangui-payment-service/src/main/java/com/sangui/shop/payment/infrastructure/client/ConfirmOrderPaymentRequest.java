package com.sangui.shop.payment.infrastructure.client;

public record ConfirmOrderPaymentRequest(
        Long shopId,
        String userId,
        Long orderId,
        String paymentNo,
        Long paidAmountCent
) {
}
