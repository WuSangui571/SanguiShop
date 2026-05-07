package com.sangui.shop.order.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<OrderRecord> findById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId);

    default List<OrderSnapshot> findSnapshotsByUser(Long shopId, String userId, int offset, int limit) {
        throw new UnsupportedOperationException("Customer order list query is not implemented");
    }

    default long countByUser(Long shopId, String userId) {
        throw new UnsupportedOperationException("Customer order count query is not implemented");
    }

    default List<OrderSnapshot> findAdminSnapshots(AdminOrderQuery query, int offset, int limit) {
        throw new UnsupportedOperationException("Admin order list query is not implemented");
    }

    default long countAdminOrders(AdminOrderQuery query) {
        throw new UnsupportedOperationException("Admin order count query is not implemented");
    }

    default List<OrderSnapshot> findFulfillmentSnapshots(FulfillmentOrderQuery query, int offset, int limit) {
        throw new UnsupportedOperationException("Fulfillment order list query is not implemented");
    }

    default long countFulfillmentOrders(FulfillmentOrderQuery query) {
        throw new UnsupportedOperationException("Fulfillment order count query is not implemented");
    }

    List<OrderRecord> findExpiredCreatedOrders(Long shopId, LocalDateTime createdBefore, int limit);

    List<OrderRecord> findCancelledOrders(Long shopId, int limit);

    default long countCompensationAttempts(OrderCompensationAttemptQuery query) {
        throw new UnsupportedOperationException("Compensation attempt history query is not implemented");
    }

    default List<OrderCompensationAttemptSummary> findCompensationAttemptSummaries(
            OrderCompensationAttemptQuery query,
            int offset,
            int limit
    ) {
        throw new UnsupportedOperationException("Compensation attempt history query is not implemented");
    }

    default List<OrderCompensationAttemptRecord> findCompensationAttemptsByOrderIds(Long shopId, List<Long> orderIds) {
        throw new UnsupportedOperationException("Compensation attempt history query is not implemented");
    }

    Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft);

    int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus);

    default int markShipped(
            Long shopId,
            Long orderId,
            String requestId,
            String carrier,
            String trackingNo,
            String traceId,
            LocalDateTime shippedAt
    ) {
        throw new UnsupportedOperationException("Shipment confirmation is not implemented");
    }

    void updateCompensationMetadata(
            Long shopId,
            Long orderId,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator,
            LocalDateTime compensatedAt
    );

    void appendCompensationAttempt(
            Long shopId,
            Long orderId,
            String orderNo,
            String reservationNo,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator
    );
}
