package com.sangui.shop.product.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductSkuStockAdjustmentRequest(
        @NotNull @Min(0) Long availableStock,
        @NotBlank @Size(max = 128) String requestId
) {
}
