package com.sangui.shop.order.client.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductReviewQueryRequest(
        @NotNull @Positive Long shopId,
        @NotNull @Positive Long productId,
        @Min(1) Integer page,
        @Min(1) @Max(50) Integer size
) {
}
