package com.sangui.shop.order.api.dto;

import java.util.List;

public record AdminOrderPageResponse(
        int page,
        int size,
        long total,
        List<AdminOrderSummaryResponse> items
) {
}
