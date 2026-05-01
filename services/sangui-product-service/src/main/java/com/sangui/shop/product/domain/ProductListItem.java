package com.sangui.shop.product.domain;

public record ProductListItem(
        Long productId,
        String productName,
        String productDescription,
        Long minPriceCent,
        Long maxPriceCent,
        ProductStatus status
) {
}
