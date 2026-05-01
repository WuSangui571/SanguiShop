package com.sangui.shop.order.domain;

import java.util.List;

public record OrderSnapshot(
        OrderRecord order,
        List<OrderItemRecord> items
) {
}
