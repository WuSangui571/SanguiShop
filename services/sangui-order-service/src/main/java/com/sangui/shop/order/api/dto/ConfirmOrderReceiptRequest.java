package com.sangui.shop.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmOrderReceiptRequest(
        @NotBlank String requestId
) {
}
