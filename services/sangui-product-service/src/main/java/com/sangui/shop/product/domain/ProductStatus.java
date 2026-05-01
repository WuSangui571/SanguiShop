package com.sangui.shop.product.domain;

public enum ProductStatus {
    DRAFT("draft"),
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ProductStatus fromValue(String value) {
        for (ProductStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported product status: " + value);
    }
}
