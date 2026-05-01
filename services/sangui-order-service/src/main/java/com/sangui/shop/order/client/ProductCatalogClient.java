package com.sangui.shop.order.client;

import java.util.List;

public interface ProductCatalogClient {

    InventoryReservationSnapshot reserveInventory(
            Long shopId,
            String reservationNo,
            List<InventoryReserveItemSnapshot> items,
            String traceId
    );

    InventoryReservationSnapshot releaseInventory(Long shopId, String reservationNo, String traceId);
}
