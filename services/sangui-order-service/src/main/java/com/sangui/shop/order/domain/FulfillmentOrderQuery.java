package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record FulfillmentOrderQuery(
        Long shopId,
        String fulfillmentStatus,
        String orderNo,
        String userId,
        LocalDateTime fromTime,
        LocalDateTime toTime
) {
}
