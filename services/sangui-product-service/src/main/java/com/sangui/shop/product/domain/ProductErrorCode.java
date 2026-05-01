package com.sangui.shop.product.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "Product does not exist"),
    PRODUCT_STATUS_INVALID("PRODUCT_STATUS_INVALID", "Product status transition is invalid"),
    PRODUCT_SKU_CODE_EXISTS("PRODUCT_SKU_CODE_EXISTS", "SKU code already exists");

    private final String code;
    private final String defaultMessage;

    ProductErrorCode(String code, String defaultMessage) {
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
