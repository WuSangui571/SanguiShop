package com.sangui.shop.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCallbackRequest(
        @NotNull @Positive Long shopId,
        @NotBlank String paymentNo,
        @NotBlank String channel,
        @NotBlank String channelTradeNo,
        @NotBlank String tradeStatus,
        @NotNull @Positive Long paidAmountCent,
        String callbackType,
        String eventTime,
        String rawPayload
) {
}
