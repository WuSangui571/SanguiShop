package com.sangui.shop.order.client;

public record ProductSkuSnapshot(
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent
) {
}
