package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReviewReplyRequest(
        @NotBlank @Size(max = 300) String content,
        @NotBlank @Size(max = 64) String requestId
) {
}
