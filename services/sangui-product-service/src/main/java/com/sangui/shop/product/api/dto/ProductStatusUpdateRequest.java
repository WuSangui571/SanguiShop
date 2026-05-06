package com.sangui.shop.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductStatusUpdateRequest(
        @NotBlank @Size(max = 32) String status,
        @NotBlank @Size(max = 128) String requestId
) {
}
