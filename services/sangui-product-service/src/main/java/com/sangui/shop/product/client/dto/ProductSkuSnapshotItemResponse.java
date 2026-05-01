package com.sangui.shop.product.client.dto;

public record ProductSkuSnapshotItemResponse(
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent
) {
}
