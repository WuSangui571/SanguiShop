package com.sangui.shop.common.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SanguiPrincipalHeaderParser {

    private SanguiPrincipalHeaderParser() {
    }

    public static Optional<SanguiPrincipal> parse(Function<String, String> headerLookup) {
        String userId = text(headerLookup.apply(SanguiIdentityHeaderNames.USER_ID));
        Long shopId = shopId(headerLookup.apply(SanguiIdentityHeaderNames.SHOP_ID));
        if (userId == null || shopId == null) {
            return Optional.empty();
        }

        return Optional.of(new SanguiPrincipal(
                userId,
                shopId,
                csv(headerLookup.apply(SanguiIdentityHeaderNames.ROLES)),
                csv(headerLookup.apply(SanguiIdentityHeaderNames.PERMISSIONS)),
                text(headerLookup.apply(SanguiIdentityHeaderNames.JWT_ID))
        ));
    }

    private static Long shopId(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Set<String> csv(String value) {
        String text = text(value);
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
