package com.sangui.shop.order.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<OrderRecord> findById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId);

    Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId);

    default Optional<OrderReviewRecord> findReviewByOrderId(Long shopId, Long orderId) {
        throw new UnsupportedOperationException("Order review query is not implemented");
    }

    default Optional<OrderReviewRecord> findReviewByRequestId(Long shopId, String userId, String requestId) {
        throw new UnsupportedOperationException("Order review idempotency query is not implemented");
    }

    default Long createReview(OrderReviewRecord review) {
        throw new UnsupportedOperationException("Order review persistence is not implemented");
    }

    default ProductReviewSummary summarizeProductReviews(Long shopId, Long productId) {
        throw new UnsupportedOperationException("Product review summary query is not implemented");
    }

    default List<ProductReviewListItem> findProductReviews(Long shopId, Long productId, int offset, int limit) {
        throw new UnsupportedOperationException("Product review list query is not implemented");
    }

    default List<AdminReviewListItem> findAdminReviews(AdminReviewQuery query, int offset, int limit) {
        throw new UnsupportedOperationException("Admin review list query is not implemented");
    }

    default long countAdminReviews(AdminReviewQuery query) {
        throw new UnsupportedOperationException("Admin review count query is not implemented");
    }

    default Optional<AdminReviewListItem> findAdminReviewById(Long shopId, Long reviewId) {
        throw new UnsupportedOperationException("Admin review detail query is not implemented");
    }

    default void updateReviewVisibility(
            Long shopId,
            Long reviewId,
            ReviewVisibilityStatus visibilityStatus,
            String reason,
            String requestId,
            String operator,
            String traceId,
            LocalDateTime visibilityUpdatedAt
    ) {
        throw new UnsupportedOperationException("Admin review visibility update is not implemented");
    }

    default void upsertReviewReply(
            Long shopId,
            Long reviewId,
            String content,
            String requestId,
            String operator,
            String traceId,
            LocalDateTime replyUpdatedAt
    ) {
        throw new UnsupportedOperationException("Admin review reply update is not implemented");
    }

    default void updateReviewReplyVisibility(
            Long shopId,
            Long reviewId,
            ReviewVisibilityStatus visibilityStatus,
            String requestId,
            String operator,
            String traceId,
            LocalDateTime replyUpdatedAt
    ) {
        throw new UnsupportedOperationException("Admin review reply visibility update is not implemented");
    }

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

    default int markCompleted(
            Long shopId,
            Long orderId,
            String requestId,
            String traceId,
            LocalDateTime completedAt
    ) {
        throw new UnsupportedOperationException("Receipt confirmation is not implemented");
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
