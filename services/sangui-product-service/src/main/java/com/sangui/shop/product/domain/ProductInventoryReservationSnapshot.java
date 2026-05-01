package com.sangui.shop.product.domain;

import java.util.List;

public record ProductInventoryReservationSnapshot(
        Long shopId,
        String reservationNo,
        ProductInventoryReservationStatus status,
        List<ProductInventoryReservationRecord> items
) {
}
