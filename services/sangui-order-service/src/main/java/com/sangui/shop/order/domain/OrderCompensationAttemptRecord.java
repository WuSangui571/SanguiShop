package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record OrderCompensationAttemptRecord(
        Long id,
        Long shopId,
        Long orderId,
        String orderNo,
        String reservationNo,
        String result,
        String errorCode,
        String reason,
        String traceId,
        String trigger,
        String operator,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
