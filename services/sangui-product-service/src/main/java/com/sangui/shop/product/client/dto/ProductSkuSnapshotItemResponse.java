package com.sangui.shop.product.client.dto;

public record ProductSkuSnapshotItemResponse(
        Long productId,
        String productName,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        Long availableStock
) {
}
