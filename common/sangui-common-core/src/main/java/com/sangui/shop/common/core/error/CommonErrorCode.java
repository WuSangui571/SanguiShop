package com.sangui.shop.common.core.error;

public enum CommonErrorCode implements ErrorCode {
    OK("OK", "ok"),
    VALIDATION_FAILED("VALIDATION_FAILED", "Request validation failed"),
    AUTH_TOKEN_MISSING("AUTH_TOKEN_MISSING", "Authentication token is missing"),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Authentication token has expired"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "Access is forbidden"),
    RATE_LIMITED("RATE_LIMITED", "Too many requests"),
    CONFIG_SECRET_MISSING("CONFIG_SECRET_MISSING", "Required secret configuration is missing"),
    DOWNSTREAM_TIMEOUT("DOWNSTREAM_TIMEOUT", "Downstream service timed out"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "Idempotency key conflicts with a different request"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String defaultMessage;

    CommonErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
