package com.sangui.shop.payment.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentCompensationAggregateResponse(
        PaymentCompensationRecordResponse payment,
        Long matchedAttemptCount,
        Long totalAttemptCount,
        OffsetDateTime latestAttemptAt,
        List<PaymentCompensationAttemptResponse> attempts
) {
}
