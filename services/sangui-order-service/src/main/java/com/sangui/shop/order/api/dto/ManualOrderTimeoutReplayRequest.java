package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ManualOrderTimeoutReplayRequest(
        @NotNull Long shopId,
        @NotNull @Positive Long orderId,
        @Positive Integer timeoutMinutes
) {
}
