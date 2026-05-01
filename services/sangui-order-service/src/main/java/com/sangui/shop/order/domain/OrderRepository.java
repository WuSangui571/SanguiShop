package com.sangui.shop.order.domain;

import java.util.Optional;

public interface OrderRepository {

    Optional<OrderRecord> findById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId);

    Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft);

    int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus);
}
