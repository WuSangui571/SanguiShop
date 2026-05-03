package com.sangui.shop.payment.api.dto;

import java.time.OffsetDateTime;

public record PaymentCompensationRecordResponse(
        Long paymentId,
        String paymentNo,
        Long orderId,
        String orderNo,
        String userId,
        String channel,
        String status,
        Long amountCent,
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
