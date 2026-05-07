package com.sangui.shop.logistics.client;

import java.util.List;

public record FulfillmentOrderPageResponse(
        int page,
        int size,
        long total,
        List<FulfillmentOrderResponse> items
) {
}
