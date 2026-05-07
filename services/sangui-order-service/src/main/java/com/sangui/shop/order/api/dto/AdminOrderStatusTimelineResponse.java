package com.sangui.shop.order.api.dto;

import java.time.OffsetDateTime;

public record AdminOrderStatusTimelineResponse(
        String status,
        OffsetDateTime occurredAt,
        String traceId
) {
}
