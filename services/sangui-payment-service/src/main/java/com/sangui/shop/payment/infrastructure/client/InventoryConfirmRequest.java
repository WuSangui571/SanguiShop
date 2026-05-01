package com.sangui.shop.payment.infrastructure.client;

public record InventoryConfirmRequest(
        Long shopId,
        String reservationNo
) {
}
