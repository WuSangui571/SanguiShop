package com.sangui.shop.common.mq;

import java.time.OffsetDateTime;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int version,
        OffsetDateTime occurredAt,
        Long shopId,
        String traceId,
        T payload
) {
}
