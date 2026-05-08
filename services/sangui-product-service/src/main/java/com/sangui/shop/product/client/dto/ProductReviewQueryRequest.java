package com.sangui.shop.product.client.dto;

public record ProductReviewQueryRequest(
        Long shopId,
        Long productId,
        Integer page,
        Integer size,
        Boolean withImages
) {
}
