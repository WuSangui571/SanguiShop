package com.sangui.shop.logistics.client;

import java.time.OffsetDateTime;

public record FulfillmentOrderQueryRequest(
        Long shopId,
        Integer page,
        Integer size,
        String fulfillmentStatus,
        String orderNo,
        String userId,
        OffsetDateTime fromTime,
        OffsetDateTime toTime
) {
}
