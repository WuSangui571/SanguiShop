package com.sangui.shop.logistics.domain;

import com.sangui.shop.common.core.error.ErrorCode;

public enum LogisticsErrorCode implements ErrorCode {
    FULFILLMENT_NOT_FOUND("FULFILLMENT_NOT_FOUND", "Fulfillment record does not exist in the current shop scope"),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "Order does not exist in the current shop scope"),
    ORDER_STATUS_INVALID("ORDER_STATUS_INVALID", "Order status does not allow shipment");

    private final String code;
    private final String defaultMessage;

    LogisticsErrorCode(String code, String defaultMessage) {
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
