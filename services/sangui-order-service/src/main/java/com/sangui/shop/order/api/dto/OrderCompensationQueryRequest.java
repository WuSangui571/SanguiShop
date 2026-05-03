package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderCompensationQueryRequest(
        @NotNull Long shopId,
        @Positive Long orderId,
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
