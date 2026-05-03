package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderCompensationAggregateResponse(
        OrderCompensationRecordResponse order,
        Long matchedAttemptCount,
        Long totalAttemptCount,
        OffsetDateTime latestAttemptAt,
        List<OrderCompensationAttemptResponse> attempts
) {
}
