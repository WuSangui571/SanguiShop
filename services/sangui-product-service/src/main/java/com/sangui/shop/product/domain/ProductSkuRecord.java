package com.sangui.shop.product.domain;

public record ProductSkuRecord(
        Long id,
        Long productId,
        String productName,
        String skuCode,
        String skuName,
        Long priceCent,
        Long availableStock,
        Long reservedStock
) {
}
