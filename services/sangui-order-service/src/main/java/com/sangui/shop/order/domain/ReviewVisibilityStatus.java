package com.sangui.shop.order.domain;

public enum ReviewVisibilityStatus {
    VISIBLE("visible"),
    HIDDEN("hidden");

    private final String value;

    ReviewVisibilityStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ReviewVisibilityStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Review visibility is required");
        }
        for (ReviewVisibilityStatus status : values()) {
            if (status.value.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported review visibility: " + value);
    }
}
