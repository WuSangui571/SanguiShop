package com.sangui.shop.logistics.client;

public record FulfillmentOrderDetailRequest(
        Long shopId,
        Long orderId
) {
}
