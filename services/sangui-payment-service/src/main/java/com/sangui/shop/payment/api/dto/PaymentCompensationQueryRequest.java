package com.sangui.shop.payment.api.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCompensationQueryRequest(
        @NotNull Long shopId,
        @Positive Long orderId,
        String paymentNo,
        String trigger,
        String result,
        String operator,
        String traceId,
        OffsetDateTime fromTime,
        OffsetDateTime toTime,
        @Positive Integer pageNo,
        @Positive Integer pageSize
) {
}
