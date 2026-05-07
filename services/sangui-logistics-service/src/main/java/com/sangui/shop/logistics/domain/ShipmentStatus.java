package com.sangui.shop.logistics.domain;

public enum ShipmentStatus {
    SHIPPED("shipped");

    private final String value;

    ShipmentStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
