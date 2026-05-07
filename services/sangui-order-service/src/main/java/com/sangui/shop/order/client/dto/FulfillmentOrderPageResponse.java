package com.sangui.shop.order.client.dto;

import java.util.List;

public record FulfillmentOrderPageResponse(
        int page,
        int size,
        long total,
        List<FulfillmentOrderResponse> items
) {
}
