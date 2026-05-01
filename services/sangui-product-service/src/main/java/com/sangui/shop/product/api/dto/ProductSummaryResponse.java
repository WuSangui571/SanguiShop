package com.sangui.shop.product.api.dto;

public record ProductSummaryResponse(
        Long productId,
        String productName,
        String productDescription,
        Long minPriceCent,
        Long maxPriceCent,
        String status
) {
}
