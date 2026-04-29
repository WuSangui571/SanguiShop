package com.sangui.shop.common.security;

import java.util.Set;

public record SanguiPrincipal(
        String userId,
        Long shopId,
        Set<String> roles,
        Set<String> permissions
) {
}
