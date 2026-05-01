package com.sangui.shop.order.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmOrderPaymentRequest(
        @NotNull @Positive Long shopId,
        @NotBlank String userId,
        @NotNull @Positive Long orderId,
        @NotBlank String paymentNo,
        @NotNull @Positive Long paidAmountCent
) {
}
