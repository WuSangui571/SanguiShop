package com.sangui.shop.order.client;

import java.util.List;

public record InventoryReservationSnapshot(
        String reservationNo,
        String status,
        List<InventoryReservationItemSnapshot> items
) {
}
