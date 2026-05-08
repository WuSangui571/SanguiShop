package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderReviewRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String content,
        @Size(max = 6) List<@NotBlank @Size(max = 2048) String> imageUrls
) {
}
