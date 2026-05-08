package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReviewReplyVisibilityRequest(
        @NotBlank @Size(max = 16) String visibility,
        @NotBlank @Size(max = 64) String requestId
) {
}
