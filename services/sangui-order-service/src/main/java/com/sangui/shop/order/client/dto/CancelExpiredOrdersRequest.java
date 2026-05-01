package com.sangui.shop.order.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CancelExpiredOrdersRequest(
        @NotNull @Positive Long shopId,
        @Positive Integer timeoutMinutes,
        @Positive Integer limit
) {
}
