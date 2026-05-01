package com.sangui.shop.payment.domain;

public record PaymentCallbackLogRecord(
        Long id,
        Long shopId,
        String paymentNo,
        String channel,
        String channelTradeNo,
        String callbackType,
        String processStatus,
        String traceId
) {
}
