package com.sangui.shop.order.domain;

import java.util.Optional;

public interface OrderRepository {

    Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId);

    Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft);
}
