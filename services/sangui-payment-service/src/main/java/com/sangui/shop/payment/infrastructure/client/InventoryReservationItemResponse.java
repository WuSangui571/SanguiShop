package com.sangui.shop.payment.infrastructure.client;

public record InventoryReservationItemResponse(
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        int quantity
) {
}
