package com.sangui.shop.product.api.dto;

public record ProductSkuResponse(
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        Long availableStock,
        Long reservedStock
) {
}
