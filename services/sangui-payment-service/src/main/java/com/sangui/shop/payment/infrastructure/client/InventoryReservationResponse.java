package com.sangui.shop.payment.infrastructure.client;

import java.util.List;

public record InventoryReservationResponse(
        String reservationNo,
        String status,
        List<InventoryReservationItemResponse> items
) {
}
