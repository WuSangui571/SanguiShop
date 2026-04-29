package com.sangui.shop.user.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    USER_USERNAME_EXISTS("USER_USERNAME_EXISTS", "Username already exists"),
    USER_MOBILE_EXISTS("USER_MOBILE_EXISTS", "Mobile already exists"),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid username/mobile or password");

    private final String code;
    private final String defaultMessage;

    UserErrorCode(String code, String defaultMessage) {
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
