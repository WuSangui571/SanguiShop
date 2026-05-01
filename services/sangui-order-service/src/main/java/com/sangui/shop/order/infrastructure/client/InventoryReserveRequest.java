package com.sangui.shop.order.infrastructure.client;

import java.util.List;

public record InventoryReserveRequest(
        Long shopId,
        String reservationNo,
        List<InventoryReserveItemRequest> items
) {
}
