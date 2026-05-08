package com.sangui.shop.order.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "Order does not exist in the current shop scope"),
    ORDER_STATUS_INVALID("ORDER_STATUS_INVALID", "Order status does not allow this operation"),
    ORDER_REVIEW_ALREADY_EXISTS("ORDER_REVIEW_ALREADY_EXISTS", "Order review already exists"),
    ORDER_REVIEW_NOT_FOUND("ORDER_REVIEW_NOT_FOUND", "Order review does not exist in the current shop scope"),
    ORDER_REVIEW_IMAGE_NOT_FOUND("ORDER_REVIEW_IMAGE_NOT_FOUND", "Order review image does not exist"),
    ORDER_REVIEW_REPLY_NOT_FOUND("ORDER_REVIEW_REPLY_NOT_FOUND", "Order review reply does not exist"),
    ORDER_PAYMENT_AMOUNT_MISMATCH("ORDER_PAYMENT_AMOUNT_MISMATCH", "Payment amount does not match order total"),
    ORDER_SKU_NOT_FOUND("ORDER_SKU_NOT_FOUND", "Order contains unknown or unavailable SKU"),
    ORDER_SKU_DUPLICATED("ORDER_SKU_DUPLICATED", "Order request contains duplicate SKU"),
    ORDER_STOCK_NOT_ENOUGH("ORDER_STOCK_NOT_ENOUGH", "Order cannot be created because stock is not enough");

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
