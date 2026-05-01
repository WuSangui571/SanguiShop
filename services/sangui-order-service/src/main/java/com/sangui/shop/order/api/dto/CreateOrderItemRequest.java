package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
        @NotNull @Positive Long skuId,
        @NotNull @Positive Integer quantity
) {
}
