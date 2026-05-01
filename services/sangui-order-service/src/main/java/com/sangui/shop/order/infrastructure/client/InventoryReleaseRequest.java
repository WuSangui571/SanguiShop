package com.sangui.shop.order.infrastructure.client;

public record InventoryReleaseRequest(
        Long shopId,
        String reservationNo
) {
}
