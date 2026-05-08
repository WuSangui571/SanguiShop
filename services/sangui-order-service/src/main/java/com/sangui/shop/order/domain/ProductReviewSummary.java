package com.sangui.shop.order.domain;

import java.util.Map;

public record ProductReviewSummary(
        long reviewCount,
        Double averageRating,
        Map<Integer, Long> ratingDistribution
) {
}
