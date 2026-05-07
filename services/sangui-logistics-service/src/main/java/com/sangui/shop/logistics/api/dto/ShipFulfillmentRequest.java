package com.sangui.shop.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipFulfillmentRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotBlank @Size(max = 64) String carrier,
        @NotBlank @Size(max = 128) String trackingNo
) {
}
