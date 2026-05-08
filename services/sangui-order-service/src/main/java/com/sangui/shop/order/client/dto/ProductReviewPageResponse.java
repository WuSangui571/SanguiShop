package com.sangui.shop.order.client.dto;

import java.util.List;

public record ProductReviewPageResponse(
        Long productId,
        Double averageRating,
        long reviewCount,
        int page,
        int size,
        List<ProductReviewItemResponse> items
) {
}
