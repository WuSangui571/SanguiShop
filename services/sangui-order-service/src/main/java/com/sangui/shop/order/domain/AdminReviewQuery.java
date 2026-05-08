package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record AdminReviewQuery(
        Long shopId,
        Long productId,
        Integer rating,
        String userId,
        ReviewVisibilityStatus visibilityStatus,
        LocalDateTime fromTime,
        LocalDateTime toTime
) {
}
