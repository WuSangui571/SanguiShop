package com.sangui.shop.order.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FulfillmentOrderDetailRequest(
        @NotNull @Positive Long shopId,
        @NotNull @Positive Long orderId
) {
}
