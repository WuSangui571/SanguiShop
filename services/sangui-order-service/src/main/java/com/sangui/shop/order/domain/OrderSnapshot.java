package com.sangui.shop.order.domain;

import java.util.List;

public record OrderSnapshot(
        OrderRecord order,
        List<OrderItemRecord> items,
        OrderReviewRecord review
) {
    public OrderSnapshot(OrderRecord order, List<OrderItemRecord> items) {
        this(order, items, null);
    }
}
