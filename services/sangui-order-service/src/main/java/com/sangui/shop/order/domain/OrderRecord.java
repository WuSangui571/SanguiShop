package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record OrderRecord(
        Long id,
        Long shopId,
        String userId,
        String orderNo,
        String requestId,
        String reservationNo,
        OrderStatus status,
        Long totalAmountCent,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String lastCompensationResult,
        String lastCompensationErrorCode,
        String lastCompensationReason,
        String lastCompensationTraceId,
        String lastCompensationTrigger,
        LocalDateTime lastCompensatedAt
) {
}
