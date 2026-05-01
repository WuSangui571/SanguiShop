package com.sangui.shop.order.client;

public record InventoryReservationItemSnapshot(
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        int quantity
) {
}
