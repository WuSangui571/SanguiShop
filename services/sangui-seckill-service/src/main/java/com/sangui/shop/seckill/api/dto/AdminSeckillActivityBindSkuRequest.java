package com.sangui.shop.seckill.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminSeckillActivityBindSkuRequest(
        @NotNull Long productId,
        @NotNull Long skuId,
        @Min(0) long activityStock,
        Long seckillPriceCent,
        @NotBlank String requestId
) {
}
