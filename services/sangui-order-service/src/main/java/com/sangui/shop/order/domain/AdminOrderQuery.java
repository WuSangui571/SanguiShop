package com.sangui.shop.order.domain;

import java.time.LocalDateTime;

public record AdminOrderQuery(
        Long shopId,
        OrderStatus status,
        String orderNo,
        String userId,
        LocalDateTime fromTime,
        LocalDateTime toTime
) {
}
