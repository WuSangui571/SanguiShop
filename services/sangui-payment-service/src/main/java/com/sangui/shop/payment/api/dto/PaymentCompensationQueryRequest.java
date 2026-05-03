package com.sangui.shop.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCompensationQueryRequest(
        @NotNull Long shopId,
        @Positive Integer minAgeMinutes,
        @Positive Integer limit
) {
}
