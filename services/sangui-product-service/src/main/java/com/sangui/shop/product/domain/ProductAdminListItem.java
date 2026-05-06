package com.sangui.shop.product.domain;

public record ProductAdminListItem(
        Long productId,
        String productName,
        String productDescription,
        Long minPriceCent,
        Long maxPriceCent,
        ProductStatus status,
        Long skuCount,
        Long availableStockTotal,
        Long reservedStockTotal
) {
}
