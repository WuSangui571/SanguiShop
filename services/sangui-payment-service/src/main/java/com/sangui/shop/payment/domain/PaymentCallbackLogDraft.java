package com.sangui.shop.payment.domain;

public record PaymentCallbackLogDraft(
        Long shopId,
        String paymentNo,
        String channel,
        String channelTradeNo,
        String callbackType,
        String payloadJson,
        String traceId
) {
}
