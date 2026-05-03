package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record OrderCompensationAttemptQuery(
        Long shopId,
        Long orderId,
        String trigger,
        String result,
        String operator,
        String traceId,
        LocalDateTime fromTime,
        LocalDateTime toTime
) {
}
