package com.sangui.shop.order.infrastructure.client;

public record InventoryConfirmRequest(
        Long shopId,
        String reservationNo
) {
}
