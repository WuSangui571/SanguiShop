package com.sangui.shop.seckill.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminSeckillActivityDraftRequest(
        Long shopId,
        String userId,
        @NotBlank String activityName,
        String description,
        @NotNull String startsAt,
        @NotNull String endsAt,
        @NotBlank String requestId,
        @Valid List<AdminSeckillActivitySkuItem> skus
) {
}
