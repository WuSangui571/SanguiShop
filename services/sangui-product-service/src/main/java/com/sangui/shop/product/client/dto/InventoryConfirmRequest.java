package com.sangui.shop.product.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryConfirmRequest(
        @NotNull @Positive Long shopId,
        @NotBlank String reservationNo
) {
}
