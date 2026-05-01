package com.sangui.shop.product.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryReserveItemRequest(
        @NotNull @Positive Long skuId,
        @NotNull @Positive Integer quantity
) {
}
