package com.sangui.shop.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record BulkPaymentReconcileRequest(
        @NotNull Long shopId,
        @NotNull Boolean dryRun,
        @NotBlank String operator,
        @Positive Integer minAgeMinutes,
        @NotNull @Positive Integer limit,
        List<@NotBlank String> paymentNos
) {
}
