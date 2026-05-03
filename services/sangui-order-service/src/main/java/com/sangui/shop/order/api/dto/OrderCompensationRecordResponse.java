package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;

public record OrderCompensationRecordResponse(
        Long orderId,
        String orderNo,
        String userId,
        String reservationNo,
        String status,
        Long totalAmountCent,
        String traceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String lastCompensationResult,
        String lastCompensationErrorCode,
        String lastCompensationReason,
        String lastCompensationTraceId,
        String lastCompensationTrigger,
        OffsetDateTime lastCompensatedAt
) {
}
