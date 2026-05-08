package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.AdminReviewPageResponse;
import com.sangui.shop.order.api.dto.AdminReviewReplyRequest;
import com.sangui.shop.order.api.dto.AdminReviewReplyVisibilityRequest;
import com.sangui.shop.order.api.dto.AdminReviewSummaryResponse;
import com.sangui.shop.order.api.dto.AdminReviewVisibilityRequest;
import com.sangui.shop.order.domain.AdminReviewListItem;
import com.sangui.shop.order.domain.AdminReviewQuery;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import com.sangui.shop.order.domain.ReviewVisibilityStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminReviewManagementServiceTest {

    private static final SanguiPrincipal REVIEW_ADMIN = new SanguiPrincipal(
            "90001",
            1L,
            java.util.Set.of(),
            java.util.Set.of(SanguiPermissionConstants.REVIEW_MANAGEMENT_ADMIN),
            "jwt-review"
    );

    private InMemoryOrderRepository orderRepository;
    private AdminReviewManagementService service;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        service = new AdminReviewManagementService(orderRepository);
    }

    @Test
    void listReviewsFiltersInsideTrustedShopScope() {
        orderRepository.seed(review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE));
        orderRepository.seed(review(2L, 1L, 302L, 402L, "10002", 3, ReviewVisibilityStatus.HIDDEN));
        orderRepository.seed(review(3L, 2L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE));

        AdminReviewPageResponse response = service.listReviews(
                REVIEW_ADMIN,
                1,
                20,
                301L,
                5,
                "10001",
                "visible",
                null,
                null
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(AdminReviewSummaryResponse::reviewId).containsExactly(1L);
        assertThat(response.items().get(0).maskedUserId()).isEqualTo("10***01");
        assertThat(response.items().get(0).imageCount()).isEqualTo(2);
        assertThat(response.items().get(0).imageUrls()).containsExactly("https://img/1.png", "https://img/2.png");
    }

    @Test
    void updateVisibilityPersistsLatestOperatorTraceAndReasonSnapshot() {
        orderRepository.seed(review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE));

        AdminReviewSummaryResponse response = service.updateVisibility(
                REVIEW_ADMIN,
                1L,
                new AdminReviewVisibilityRequest(" hidden ", " Contains sensitive content ", " vis-001 "),
                "trace-vis-001"
        );

        assertThat(response.visibilityStatus()).isEqualTo("hidden");
        assertThat(response.visibilityReason()).isEqualTo("Contains sensitive content");
        assertThat(response.visibilityRequestId()).isEqualTo("vis-001");
        assertThat(response.visibilityOperator()).isEqualTo("90001");
        assertThat(response.visibilityTraceId()).isEqualTo("trace-vis-001");
        assertThat(response.imageUrls()).containsExactly("https://img/1.png", "https://img/2.png");
    }

    @Test
    void duplicateVisibilityRequestReturnsCurrentSnapshot() {
        AdminReviewListItem item = review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.HIDDEN);
        orderRepository.seed(withVisibilityRequest(item, "vis-001"));

        AdminReviewSummaryResponse response = service.updateVisibility(
                REVIEW_ADMIN,
                1L,
                new AdminReviewVisibilityRequest("hidden", null, "vis-001"),
                "trace-replay"
        );

        assertThat(response.visibilityStatus()).isEqualTo("hidden");
        assertThat(orderRepository.updateCalls).isZero();
    }

    @Test
    void duplicateVisibilityRequestWithDifferentTargetConflicts() {
        AdminReviewListItem item = review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.HIDDEN);
        orderRepository.seed(withVisibilityRequest(item, "vis-001"));

        assertThatThrownBy(() -> service.updateVisibility(
                REVIEW_ADMIN,
                1L,
                new AdminReviewVisibilityRequest("visible", null, "vis-001"),
                "trace-conflict"
        ))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void upsertReplyTrimsContentAndPersistsOperatorTraceSnapshot() {
        orderRepository.seed(review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE));

        AdminReviewSummaryResponse response = service.upsertReply(
                REVIEW_ADMIN,
                1L,
                new AdminReviewReplyRequest(" Thanks for the feedback. ", " reply-001 "),
                "trace-reply-001"
        );

        assertThat(response.replyContent()).isEqualTo("Thanks for the feedback.");
        assertThat(response.replyVisibilityStatus()).isEqualTo("visible");
        assertThat(response.replyRequestId()).isEqualTo("reply-001");
        assertThat(response.replyOperator()).isEqualTo("90001");
        assertThat(response.replyTraceId()).isEqualTo("trace-reply-001");
    }

    @Test
    void duplicateReplyRequestReturnsCurrentSnapshot() {
        AdminReviewListItem item = withReplyRequest(
                review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE),
                "Thanks.",
                ReviewVisibilityStatus.VISIBLE,
                "reply-001"
        );
        orderRepository.seed(item);

        AdminReviewSummaryResponse response = service.upsertReply(
                REVIEW_ADMIN,
                1L,
                new AdminReviewReplyRequest("Thanks.", "reply-001"),
                "trace-replay"
        );

        assertThat(response.replyContent()).isEqualTo("Thanks.");
        assertThat(orderRepository.replyUpdateCalls).isZero();
    }

    @Test
    void duplicateReplyRequestWithDifferentContentConflicts() {
        AdminReviewListItem item = withReplyRequest(
                review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE),
                "Thanks.",
                ReviewVisibilityStatus.VISIBLE,
                "reply-001"
        );
        orderRepository.seed(item);

        assertThatThrownBy(() -> service.upsertReply(
                REVIEW_ADMIN,
                1L,
                new AdminReviewReplyRequest("Changed.", "reply-001"),
                "trace-conflict"
        ))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void updateReplyVisibilityHidesExistingReply() {
        AdminReviewListItem item = withReplyRequest(
                review(1L, 1L, 301L, 401L, "10001", 5, ReviewVisibilityStatus.VISIBLE),
                "Thanks.",
                ReviewVisibilityStatus.VISIBLE,
                "reply-001"
        );
        orderRepository.seed(item);

        AdminReviewSummaryResponse response = service.updateReplyVisibility(
                REVIEW_ADMIN,
                1L,
                new AdminReviewReplyVisibilityRequest("hidden", "reply-vis-001"),
                "trace-reply-vis"
        );

        assertThat(response.replyVisibilityStatus()).isEqualTo("hidden");
        assertThat(response.replyRequestId()).isEqualTo("reply-vis-001");
        assertThat(response.replyTraceId()).isEqualTo("trace-reply-vis");
    }

    @Test
    void rejectsCompensationOnlyPermission() {
        SanguiPrincipal compensationOnly = new SanguiPrincipal(
                "90002",
                1L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN),
                "jwt-comp"
        );

        assertThatThrownBy(() -> service.listReviews(compensationOnly, 1, 20, null, null, null, null, null, null))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void rejectsInvalidFilterValues() {
        assertThatThrownBy(() -> service.listReviews(REVIEW_ADMIN, 1, 20, 0L, null, null, null, null, null))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(exception.httpStatus()).isEqualTo(400);
                });

        assertThatThrownBy(() -> service.listReviews(REVIEW_ADMIN, 1, 20, null, 6, null, null, null, null))
                .isInstanceOf(SanguiException.class);
    }

    private AdminReviewListItem review(
            Long reviewId,
            Long shopId,
            Long productId,
            Long skuId,
            String userId,
            int rating,
            ReviewVisibilityStatus visibilityStatus
    ) {
        LocalDateTime createdAt = LocalDateTime.parse("2026-05-08T10:00:00").plusSeconds(reviewId);
        return new AdminReviewListItem(
                reviewId,
                shopId,
                1000L + reviewId,
                "ORD-" + reviewId,
                productId,
                skuId,
                "SKU " + skuId,
                userId,
                rating,
                "Review " + reviewId,
                List.of("https://img/1.png", "https://img/2.png"),
                visibilityStatus,
                null,
                null,
                null,
                null,
                null,
                null,
                ReviewVisibilityStatus.VISIBLE,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt
        );
    }

    private AdminReviewListItem withVisibilityRequest(AdminReviewListItem item, String requestId) {
        return new AdminReviewListItem(
                item.reviewId(),
                item.shopId(),
                item.orderId(),
                item.orderNo(),
                item.productId(),
                item.skuId(),
                item.skuName(),
                item.userId(),
                item.rating(),
                item.content(),
                item.imageUrls(),
                item.visibilityStatus(),
                item.visibilityReason(),
                requestId,
                item.visibilityOperator(),
                item.visibilityTraceId(),
                item.visibilityUpdatedAt(),
                item.replyContent(),
                item.replyVisibilityStatus(),
                item.replyRequestId(),
                item.replyOperator(),
                item.replyTraceId(),
                item.replyUpdatedAt(),
                item.createdAt(),
                item.updatedAt()
        );
    }

    private AdminReviewListItem withReplyRequest(
            AdminReviewListItem item,
            String content,
            ReviewVisibilityStatus replyVisibilityStatus,
            String requestId
    ) {
        return new AdminReviewListItem(
                item.reviewId(),
                item.shopId(),
                item.orderId(),
                item.orderNo(),
                item.productId(),
                item.skuId(),
                item.skuName(),
                item.userId(),
                item.rating(),
                item.content(),
                item.imageUrls(),
                item.visibilityStatus(),
                item.visibilityReason(),
                item.visibilityRequestId(),
                item.visibilityOperator(),
                item.visibilityTraceId(),
                item.visibilityUpdatedAt(),
                content,
                replyVisibilityStatus,
                requestId,
                item.replyOperator(),
                item.replyTraceId(),
                item.replyUpdatedAt(),
                item.createdAt(),
                item.updatedAt()
        );
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<Long, AdminReviewListItem> reviews = new LinkedHashMap<>();
        private int updateCalls;
        private int replyUpdateCalls;

        private void seed(AdminReviewListItem item) {
            reviews.put(item.reviewId(), item);
        }

        @Override
        public List<AdminReviewListItem> findAdminReviews(AdminReviewQuery query, int offset, int limit) {
            return reviews.values().stream()
                    .filter(item -> item.shopId().equals(query.shopId()))
                    .filter(item -> query.productId() == null || item.productId().equals(query.productId()))
                    .filter(item -> query.rating() == null || item.rating().equals(query.rating()))
                    .filter(item -> query.userId() == null || item.userId().equals(query.userId()))
                    .filter(item -> query.visibilityStatus() == null || item.visibilityStatus() == query.visibilityStatus())
                    .filter(item -> query.fromTime() == null || !item.createdAt().isBefore(query.fromTime()))
                    .filter(item -> query.toTime() == null || !item.createdAt().isAfter(query.toTime()))
                    .sorted(Comparator.comparing(AdminReviewListItem::createdAt).reversed()
                            .thenComparing(AdminReviewListItem::reviewId, Comparator.reverseOrder()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countAdminReviews(AdminReviewQuery query) {
            return findAdminReviews(query, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public Optional<AdminReviewListItem> findAdminReviewById(Long shopId, Long reviewId) {
            AdminReviewListItem item = reviews.get(reviewId);
            if (item == null || !item.shopId().equals(shopId)) {
                return Optional.empty();
            }
            return Optional.of(item);
        }

        @Override
        public void updateReviewVisibility(
                Long shopId,
                Long reviewId,
                ReviewVisibilityStatus visibilityStatus,
                String reason,
                String requestId,
                String operator,
                String traceId,
                LocalDateTime visibilityUpdatedAt
        ) {
            updateCalls++;
            AdminReviewListItem item = reviews.get(reviewId);
            reviews.put(reviewId, new AdminReviewListItem(
                    item.reviewId(),
                    item.shopId(),
                    item.orderId(),
                    item.orderNo(),
                    item.productId(),
                    item.skuId(),
                    item.skuName(),
                    item.userId(),
                    item.rating(),
                    item.content(),
                    item.imageUrls(),
                    visibilityStatus,
                    reason,
                    requestId,
                    operator,
                    traceId,
                    visibilityUpdatedAt,
                    item.replyContent(),
                    item.replyVisibilityStatus(),
                    item.replyRequestId(),
                    item.replyOperator(),
                    item.replyTraceId(),
                    item.replyUpdatedAt(),
                    item.createdAt(),
                    visibilityUpdatedAt
            ));
        }

        @Override
        public void upsertReviewReply(
                Long shopId,
                Long reviewId,
                String content,
                String requestId,
                String operator,
                String traceId,
                LocalDateTime replyUpdatedAt
        ) {
            replyUpdateCalls++;
            AdminReviewListItem item = reviews.get(reviewId);
            reviews.put(reviewId, new AdminReviewListItem(
                    item.reviewId(),
                    item.shopId(),
                    item.orderId(),
                    item.orderNo(),
                    item.productId(),
                    item.skuId(),
                    item.skuName(),
                    item.userId(),
                    item.rating(),
                    item.content(),
                    item.imageUrls(),
                    item.visibilityStatus(),
                    item.visibilityReason(),
                    item.visibilityRequestId(),
                    item.visibilityOperator(),
                    item.visibilityTraceId(),
                    item.visibilityUpdatedAt(),
                    content,
                    ReviewVisibilityStatus.VISIBLE,
                    requestId,
                    operator,
                    traceId,
                    replyUpdatedAt,
                    item.createdAt(),
                    replyUpdatedAt
            ));
        }

        @Override
        public void updateReviewReplyVisibility(
                Long shopId,
                Long reviewId,
                ReviewVisibilityStatus visibilityStatus,
                String requestId,
                String operator,
                String traceId,
                LocalDateTime replyUpdatedAt
        ) {
            replyUpdateCalls++;
            AdminReviewListItem item = reviews.get(reviewId);
            reviews.put(reviewId, new AdminReviewListItem(
                    item.reviewId(),
                    item.shopId(),
                    item.orderId(),
                    item.orderNo(),
                    item.productId(),
                    item.skuId(),
                    item.skuName(),
                    item.userId(),
                    item.rating(),
                    item.content(),
                    item.imageUrls(),
                    item.visibilityStatus(),
                    item.visibilityReason(),
                    item.visibilityRequestId(),
                    item.visibilityOperator(),
                    item.visibilityTraceId(),
                    item.visibilityUpdatedAt(),
                    item.replyContent(),
                    visibilityStatus,
                    requestId,
                    operator,
                    traceId,
                    replyUpdatedAt,
                    item.createdAt(),
                    replyUpdatedAt
            ));
        }

        @Override
        public Optional<OrderRecord> findById(Long shopId, Long orderId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId) {
            return Optional.empty();
        }

        @Override
        public List<OrderRecord> findExpiredCreatedOrders(Long shopId, LocalDateTime createdBefore, int limit) {
            return List.of();
        }

        @Override
        public List<OrderRecord> findCancelledOrders(Long shopId, int limit) {
            return List.of();
        }

        @Override
        public Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft) {
            return 0L;
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            return 0;
        }

        @Override
        public void updateCompensationMetadata(
                Long shopId,
                Long orderId,
                String result,
                String errorCode,
                String reason,
                String traceId,
                String trigger,
                String operator,
                LocalDateTime compensatedAt
        ) {
        }

        @Override
        public void appendCompensationAttempt(
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
        ) {
        }
    }
}
