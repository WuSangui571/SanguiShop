package com.sangui.shop.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        Long shopId,
        String userId,
        @NotNull @Positive Long orderId,
        @NotBlank String paymentNo,
        @NotBlank String channel
) {
}
