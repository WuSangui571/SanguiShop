package com.sangui.shop.order.infrastructure.client;

public record ProductSkuSnapshotItemResponse(
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent
) {
}
