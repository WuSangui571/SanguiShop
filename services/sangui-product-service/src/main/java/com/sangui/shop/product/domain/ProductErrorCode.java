package com.sangui.shop.product.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "Product does not exist"),
    PRODUCT_SKU_NOT_FOUND("PRODUCT_SKU_NOT_FOUND", "SKU does not exist or is unavailable"),
    PRODUCT_STATUS_INVALID("PRODUCT_STATUS_INVALID", "Product status transition is invalid"),
    PRODUCT_SKU_CODE_EXISTS("PRODUCT_SKU_CODE_EXISTS", "SKU code already exists"),
    PRODUCT_STOCK_NOT_ENOUGH("PRODUCT_STOCK_NOT_ENOUGH", "SKU stock is not enough"),
    PRODUCT_INVENTORY_RESERVATION_NOT_FOUND("PRODUCT_INVENTORY_RESERVATION_NOT_FOUND", "Inventory reservation does not exist"),
    PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID(
            "PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID",
            "Inventory reservation status does not allow this operation"
    );

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
