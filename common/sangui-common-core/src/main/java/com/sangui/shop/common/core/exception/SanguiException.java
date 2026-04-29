package com.sangui.shop.common.core.exception;

import com.sangui.shop.common.core.error.ErrorCode;

public class SanguiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    public SanguiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), 400);
    }

    public SanguiException(ErrorCode errorCode, String message) {
        this(errorCode, message, 400);
    }

    public SanguiException(ErrorCode errorCode, int httpStatus) {
        this(errorCode, errorCode.defaultMessage(), httpStatus);
    }

    public SanguiException(ErrorCode errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
