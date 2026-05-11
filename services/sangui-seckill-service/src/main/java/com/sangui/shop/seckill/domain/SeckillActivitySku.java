package com.sangui.shop.seckill.domain;

public record SeckillActivitySku(
        Long id,
        Long activityId,
        Long productId,
        String productName,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        Long seckillPriceCent,
        Long availableStock,
        Long activityStock,
        Long soldCount,
        String requestId
) {
}
