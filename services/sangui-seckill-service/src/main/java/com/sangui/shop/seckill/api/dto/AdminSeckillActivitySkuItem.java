package com.sangui.shop.seckill.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminSeckillActivitySkuItem(
        @NotNull Long productId,
        @NotNull Long skuId,
        @Min(0) long activityStock,
        @Min(1) long seckillPriceCent
) {
}
