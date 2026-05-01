package com.sangui.shop.common.security;

import java.util.Optional;

public final class SanguiSecurityContext {

    private static final ThreadLocal<SanguiPrincipal> CURRENT_PRINCIPAL = new ThreadLocal<>();

    private SanguiSecurityContext() {
    }

    public static Optional<SanguiPrincipal> currentPrincipal() {
        return Optional.ofNullable(CURRENT_PRINCIPAL.get());
    }

    public static void setPrincipal(SanguiPrincipal principal) {
        if (principal == null) {
            clear();
            return;
        }
        CURRENT_PRINCIPAL.set(principal);
    }

    public static void clear() {
        CURRENT_PRINCIPAL.remove();
    }
}
