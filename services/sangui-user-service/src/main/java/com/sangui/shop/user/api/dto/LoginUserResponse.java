package com.sangui.shop.user.api.dto;

import java.util.List;

public record LoginUserResponse(
        Long userId,
        Long shopId,
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        List<String> roles
) {
}
