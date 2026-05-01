package com.sangui.shop.payment.api.dto;

public record PaymentCallbackResponse(
        String paymentNo,
        String channel,
        String channelTradeNo,
        String paymentStatus,
        String processStatus
) {
}
