package com.sangui.shop.seckill.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSeckillActivityStatusUpdateRequest(
        @NotBlank String status,
        @NotBlank String requestId
) {
}
