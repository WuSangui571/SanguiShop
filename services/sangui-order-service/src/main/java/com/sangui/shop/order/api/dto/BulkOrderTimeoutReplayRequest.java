package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record BulkOrderTimeoutReplayRequest(
        @NotNull Long shopId,
        @NotNull Boolean dryRun,
        @NotBlank String operator,
        @Positive Integer timeoutMinutes,
        @NotNull @Positive Integer limit,
        List<@Positive Long> orderIds
) {
}
