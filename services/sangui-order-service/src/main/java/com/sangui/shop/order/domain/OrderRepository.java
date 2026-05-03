package com.sangui.shop.order.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<OrderRecord> findById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId);

    List<OrderRecord> findExpiredCreatedOrders(Long shopId, LocalDateTime createdBefore, int limit);

    List<OrderRecord> findCancelledOrders(Long shopId, int limit);

    Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft);

    int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus);

    void updateCompensationMetadata(
            Long shopId,
            Long orderId,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            LocalDateTime compensatedAt
    );
}
