package com.sangui.shop.payment.domain;

import java.time.LocalDateTime;

public record PaymentCompensationAttemptSummary(
        Long paymentId,
        String paymentNo,
        LocalDateTime latestAttemptAt,
        long matchedAttemptCount
) {
}
