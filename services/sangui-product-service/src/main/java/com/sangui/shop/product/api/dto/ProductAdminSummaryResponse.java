package com.sangui.shop.product.api.dto;

public record ProductAdminSummaryResponse(
        Long productId,
        String productName,
        String productDescription,
        Long minPriceCent,
        Long maxPriceCent,
        String status,
        Long skuCount,
        Long availableStockTotal,
        Long reservedStockTotal
) {
}
