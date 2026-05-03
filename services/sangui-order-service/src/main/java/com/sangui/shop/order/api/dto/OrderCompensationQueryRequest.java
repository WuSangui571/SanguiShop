package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderCompensationQueryRequest(
        @NotNull Long shopId,
        @Positive Integer timeoutMinutes,
        @Positive Integer limit
) {
}
