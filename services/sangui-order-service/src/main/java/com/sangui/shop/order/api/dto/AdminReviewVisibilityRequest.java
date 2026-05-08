package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReviewVisibilityRequest(
        @NotBlank @Size(max = 16) String visibility,
        @Size(max = 200) String reason,
        @NotBlank @Size(max = 64) String requestId
) {
}
