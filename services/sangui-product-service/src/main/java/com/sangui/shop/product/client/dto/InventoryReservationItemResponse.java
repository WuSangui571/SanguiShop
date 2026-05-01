package com.sangui.shop.product.client.dto;

public record InventoryReservationItemResponse(
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        int quantity
) {
}
