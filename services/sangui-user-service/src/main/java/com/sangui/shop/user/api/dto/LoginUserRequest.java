package com.sangui.shop.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LoginUserRequest(
        @NotNull @Positive Long shopId,
        @NotBlank @Size(max = 64) String usernameOrMobile,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
