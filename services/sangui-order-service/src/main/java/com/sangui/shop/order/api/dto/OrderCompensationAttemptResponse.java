package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;

public record OrderCompensationAttemptResponse(
        Long attemptId,
        Long orderId,
        String orderNo,
        String reservationNo,
        String result,
        String errorCode,
        String reason,
        String traceId,
        String trigger,
        String operator,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
