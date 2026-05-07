package com.sangui.shop.logistics.domain;

import java.util.Optional;

public interface ShipmentRepository {

    Optional<ShipmentRecord> findByOrderId(Long shopId, Long orderId);

    Optional<ShipmentRecord> findByRequestId(Long shopId, String requestId);

    Long create(ShipmentRecord record);
}
