package com.sangui.shop.order.infrastructure.client;

public record InventoryReserveItemRequest(
        Long skuId,
        Integer quantity
) {
}
