package com.sangui.shop.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotNull @Positive Long shopId,
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9_]+$") String username,
        @NotBlank @Size(max = 32) @Pattern(regexp = "^1[3-9]\\d{9}$") String mobile,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
