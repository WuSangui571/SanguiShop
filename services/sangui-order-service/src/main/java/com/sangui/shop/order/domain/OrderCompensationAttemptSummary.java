package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record OrderCompensationAttemptSummary(
        Long orderId,
        LocalDateTime latestAttemptAt,
        long matchedAttemptCount
) {
}
