package com.sangui.shop.user.domain;

public record UserAccount(
        Long id,
        Long shopId,
        String username,
        String mobile,
        String passwordHash,
        String status
) {
}
