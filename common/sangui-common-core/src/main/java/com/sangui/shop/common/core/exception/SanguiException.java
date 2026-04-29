package com.sangui.shop.common.core.exception;

import com.sangui.shop.common.core.error.ErrorCode;

public class SanguiException extends RuntimeException {

    private final ErrorCode errorCode;

    public SanguiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public SanguiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
