package com.sangui.shop.seckill.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum SeckillErrorCode implements ErrorCode {
    SECKILL_ACTIVITY_NOT_FOUND("SECKILL_ACTIVITY_NOT_FOUND", "Seckill activity does not exist in the current shop scope"),
    SECKILL_ACTIVITY_STATUS_INVALID("SECKILL_ACTIVITY_STATUS_INVALID", "Seckill activity status does not allow this operation"),
    SECKILL_ACTIVITY_SKU_NOT_FOUND("SECKILL_ACTIVITY_SKU_NOT_FOUND", "SKU is not bound to this seckill activity"),
    PRODUCT_SKU_NOT_FOUND("PRODUCT_SKU_NOT_FOUND", "Product SKU does not exist or is not available"),
    PRODUCT_STOCK_NOT_ENOUGH("PRODUCT_STOCK_NOT_ENOUGH", "Product SKU stock is not enough for the requested activity stock");

    private final String code;
    private final String defaultMessage;

    SeckillErrorCode(String code, String defaultMessage) {
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
