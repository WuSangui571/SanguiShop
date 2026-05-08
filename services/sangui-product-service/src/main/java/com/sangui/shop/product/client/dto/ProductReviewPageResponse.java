package com.sangui.shop.product.client.dto;

import java.util.List;
import java.util.Map;

public record ProductReviewPageResponse(
        Long productId,
        Double averageRating,
        long reviewCount,
        Map<Integer, Long> ratingDistribution,
        int page,
        int size,
        List<ProductReviewItemResponse> items
) {
}
