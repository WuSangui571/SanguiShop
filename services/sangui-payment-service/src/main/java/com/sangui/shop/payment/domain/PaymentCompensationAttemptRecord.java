package com.sangui.shop.payment.domain;

import java.time.LocalDateTime;

public record PaymentCompensationAttemptRecord(
        Long id,
        Long shopId,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
