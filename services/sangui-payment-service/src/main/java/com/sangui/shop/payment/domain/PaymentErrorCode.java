package com.sangui.shop.payment.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_ORDER_NOT_FOUND("PAYMENT_ORDER_NOT_FOUND", "Order does not exist or cannot be paid"),
    PAYMENT_ORDER_STATUS_INVALID("PAYMENT_ORDER_STATUS_INVALID", "Order status does not allow payment"),
    PAYMENT_AMOUNT_MISMATCH("PAYMENT_AMOUNT_MISMATCH", "Payment amount does not match the order total");

    private final String code;
    private final String defaultMessage;

    PaymentErrorCode(String code, String defaultMessage) {
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
