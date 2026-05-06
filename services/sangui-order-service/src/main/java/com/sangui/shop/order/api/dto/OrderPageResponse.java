package com.sangui.shop.order.api.dto;

import java.util.List;

public record OrderPageResponse(
        int page,
        int size,
        long total,
        List<OrderResponse> items
) {
}
