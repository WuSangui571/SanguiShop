package com.sangui.shop.logistics.client;

public record ConfirmOrderShipmentRequest(
        Long shopId,
        Long orderId,
        String requestId,
        String carrier,
        String trackingNo
) {
}
