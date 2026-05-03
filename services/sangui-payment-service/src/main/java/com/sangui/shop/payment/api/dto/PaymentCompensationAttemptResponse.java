package com.sangui.shop.payment.api.dto;

import java.time.OffsetDateTime;

public record PaymentCompensationAttemptResponse(
        Long attemptId,
        Long paymentId,
        Long orderId,
        String paymentNo,
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
