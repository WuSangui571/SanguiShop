package com.sangui.shop.payment.domain;

import java.time.LocalDateTime;

public record PaymentCompensationAttemptQuery(
        Long shopId,
        Long orderId,
        String paymentNo,
        String trigger,
        String result,
        String operator,
        String traceId,
        LocalDateTime fromTime,
        LocalDateTime toTime
) {
}
