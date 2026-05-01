package com.sangui.shop.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        Long shopId,
        String userId,
        @NotBlank String requestId,
        @NotEmpty List<@Valid CreateOrderItemRequest> items
) {
}
