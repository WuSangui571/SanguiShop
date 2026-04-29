package com.sangui.shop.user.api.dto;

import java.util.List;

public record RegisterUserResponse(
        Long userId,
        Long shopId,
        String username,
        String mobile,
        List<String> roles
) {
}
