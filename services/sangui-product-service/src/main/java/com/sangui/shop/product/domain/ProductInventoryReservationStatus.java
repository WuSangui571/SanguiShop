package com.sangui.shop.product.domain;

public enum ProductInventoryReservationStatus {
    RESERVED("reserved"),
    CONFIRMED("confirmed"),
    RELEASED("released");

    private final String value;

    ProductInventoryReservationStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ProductInventoryReservationStatus fromValue(String value) {
        for (ProductInventoryReservationStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown inventory reservation status: " + value);
    }
}
