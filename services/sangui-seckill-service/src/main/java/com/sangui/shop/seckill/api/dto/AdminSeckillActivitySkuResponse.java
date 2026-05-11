package com.sangui.shop.seckill.api.dto;

import com.sangui.shop.seckill.domain.SeckillActivitySku;

public record AdminSeckillActivitySkuResponse(
        Long productId,
        String productName,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        Long seckillPriceCent,
        Long availableStock,
        Long activityStock,
        Long soldCount
) {
    public static AdminSeckillActivitySkuResponse from(SeckillActivitySku sku) {
        return new AdminSeckillActivitySkuResponse(
                sku.productId(),
                sku.productName(),
                sku.skuId(),
                sku.skuCode(),
                sku.skuName(),
                sku.priceCent(),
                sku.seckillPriceCent(),
                sku.availableStock(),
                sku.activityStock(),
                sku.soldCount()
        );
    }
}
