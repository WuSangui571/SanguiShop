package com.sangui.shop.product.domain;

public record ProductRecord(
        Long id,
        Long shopId,
        String productName,
        String productDescription,
        ProductStatus status,
        String createdBy,
        String updatedBy
) {
}
