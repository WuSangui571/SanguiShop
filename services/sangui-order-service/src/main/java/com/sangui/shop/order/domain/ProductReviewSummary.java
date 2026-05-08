package com.sangui.shop.order.domain;

public record ProductReviewSummary(
        long reviewCount,
        Double averageRating
) {
}
