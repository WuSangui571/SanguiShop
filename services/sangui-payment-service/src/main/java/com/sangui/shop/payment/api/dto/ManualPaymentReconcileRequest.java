package com.sangui.shop.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManualPaymentReconcileRequest(
        @NotNull Long shopId,
        @NotBlank String paymentNo
) {
}
