package com.sangui.shop.order.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {
    ORDER_SKU_NOT_FOUND("ORDER_SKU_NOT_FOUND", "Order contains unknown or unavailable SKU"),
    ORDER_SKU_DUPLICATED("ORDER_SKU_DUPLICATED", "Order request contains duplicate SKU");

    private final String code;
    private final String defaultMessage;

    OrderErrorCode(String code, String defaultMessage) {
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
