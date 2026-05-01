package com.sangui.shop.product.api.dto;

import java.util.List;

public record ProductDetailResponse(
        Long productId,
        String productName,
        String productDescription,
        String status,
        List<ProductSkuResponse> skus
) {
}
