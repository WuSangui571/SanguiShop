package com.sangui.shop.user.application;

public record TokenInfo(
        String accessToken,
        long expiresInSeconds
) {
}
