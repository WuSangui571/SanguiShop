package com.sangui.shop.product.client.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record InventoryReserveRequest(
        @NotNull @Positive Long shopId,
        @NotBlank String reservationNo,
        @NotEmpty List<@Valid InventoryReserveItemRequest> items
) {
}
