package com.sangui.shop.product.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertProductSkuRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String skuCode,
        @NotBlank @Size(max = 128) String skuName,
        @NotNull @Min(1) Long priceCent,
        @Min(0) Long availableStock
) {
}
