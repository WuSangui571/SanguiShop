package com.sangui.shop.user.api.dto;

import java.util.List;

public record OpsSessionResponse(
        Long userId,
        Long shopId,
        String username,
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        List<String> roles,
        List<String> permissions
) {
}
