package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelOrderRequest(
        @NotBlank @Size(max = 64) String requestId
) {
}
