package com.sangui.shop.order.client.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

public record FulfillmentOrderQueryRequest(
        @NotNull @Positive Long shopId,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer size,
        String fulfillmentStatus,
        String orderNo,
        String userId,
        OffsetDateTime fromTime,
        OffsetDateTime toTime
) {
}
