package com.sangui.shop.order.domain;

public enum OrderStatus {
    CREATED("created"),
    CANCELLED("cancelled"),
    PAID("paid");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + value);
    }
}
