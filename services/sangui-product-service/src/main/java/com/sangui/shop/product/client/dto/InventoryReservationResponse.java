package com.sangui.shop.product.client.dto;

import java.util.List;

public record InventoryReservationResponse(
        String reservationNo,
        String status,
        List<InventoryReservationItemResponse> items
) {
}
