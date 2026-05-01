package com.sangui.shop.order.client;

public record InventoryReserveItemSnapshot(
        Long skuId,
        int quantity
) {
}
