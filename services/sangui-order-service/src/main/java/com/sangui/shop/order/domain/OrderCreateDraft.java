package com.sangui.shop.order.domain;

import java.util.List;

public record OrderCreateDraft(
        String requestId,
        List<OrderItemDraft> items
) {
    public long totalAmountCent() {
        return items.stream()
                .mapToLong(OrderItemDraft::lineAmountCent)
                .sum();
    }
}
