package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.CreateOrderReviewRequest;
import com.sangui.shop.order.api.dto.OrderReviewResponse;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderReviewRecord;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderReviewServiceTest {

    private static final SanguiPrincipal USER_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("USER"),
            java.util.Set.of("order:write"),
            "jwt-user"
    );

    private InMemoryOrderRepository orderRepository;
    private OrderReviewService orderReviewService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderReviewService = new OrderReviewService(orderRepository);
    }

    @Test
    void createReviewAcceptsCompletedOwnedOrder() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.COMPLETED);

        OrderReviewResponse response = orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest(
                        " review-001 ",
                        5,
                        " good ",
                        List.of(" /api/uploads/review-images/review-a.jpg ")
                ),
                "trace-review"
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderNo()).isEqualTo("ORD-001");
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("good");
        assertThat(response.imageUrls()).containsExactly("/api/uploads/review-images/review-a.jpg");
        assertThat(response.requestId()).isEqualTo("review-001");
        assertThat(response.traceId()).isEqualTo("trace-review");
        assertThat(orderRepository.findSnapshotById(1L, orderId).orElseThrow().review()).isNotNull();
    }

    @Test
    void createReviewReplaysSameRequestIdWhenPayloadMatches() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.COMPLETED);
        OrderReviewResponse first = orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of()),
                "trace-review"
        );

        OrderReviewResponse replay = orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of()),
                "trace-review-replay"
        );

        assertThat(replay.orderReviewId()).isEqualTo(first.orderReviewId());
        assertThat(orderRepository.reviewsById).hasSize(1);
    }

    @Test
    void createReviewRejectsSameRequestIdWithDifferentPayload() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.COMPLETED);
        orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of()),
                "trace-review"
        );

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 4, "good", List.of()),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void createReviewRejectsSameRequestIdWithDifferentImageUrls() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.COMPLETED);
        orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of("/api/uploads/review-images/a.jpg")),
                "trace-review"
        );

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of("/api/uploads/review-images/b.jpg")),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void createReviewRejectsSameOrderWithDifferentRequestId() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.COMPLETED);
        orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of()),
                "trace-review"
        );

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-002", 5, "good", List.of()),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_REVIEW_ALREADY_EXISTS.code());
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void createReviewRejectsNonCompletedStatuses() {
        for (OrderStatus status : List.of(OrderStatus.CREATED, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.CANCELLED)) {
            Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-" + status.value(), status);

            assertThatThrownBy(() -> orderReviewService.createReview(
                    USER_PRINCIPAL,
                    orderId,
                    new CreateOrderReviewRequest("review-" + status.value(), 5, "good", List.of()),
                    "trace-review"
            )).isInstanceOfSatisfying(SanguiException.class, exception -> {
                assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID.code());
                assertThat(exception.httpStatus()).isEqualTo(409);
            });
        }
    }

    @Test
    void createReviewUsesPrincipalOwnershipScope() {
        Long orderId = orderRepository.seedOrder(1L, "20002", "ORD-OTHER", OrderStatus.COMPLETED);

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of()),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.code());
            assertThat(exception.httpStatus()).isEqualTo(404);
        });
    }

    @Test
    void createReviewValidatesInputBoundaries() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.COMPLETED);

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest(" ", 5, "good", List.of()),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 6, "good", List.of()),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of("")),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of("https://cdn.example/review.jpg")),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));

        assertThatThrownBy(() -> orderReviewService.createReview(
                USER_PRINCIPAL,
                orderId,
                new CreateOrderReviewRequest("review-001", 5, "good", List.of("file:///tmp/review.jpg")),
                "trace-review"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED"));
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<Long, OrderSnapshot> snapshotsById = new LinkedHashMap<>();
        private final Map<Long, OrderReviewRecord> reviewsById = new LinkedHashMap<>();
        private long nextOrderId = 10000L;
        private long nextReviewId = 9000L;

        @Override
        public Optional<OrderRecord> findById(Long shopId, Long orderId) {
            return findSnapshotById(shopId, orderId).map(OrderSnapshot::order);
        }

        @Override
        public Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || !java.util.Objects.equals(snapshot.order().shopId(), shopId)) {
                return Optional.empty();
            }
            return Optional.of(snapshot);
        }

        @Override
        public Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderReviewRecord> findReviewByOrderId(Long shopId, Long orderId) {
            return reviewsById.values().stream()
                    .filter(review -> java.util.Objects.equals(review.shopId(), shopId))
                    .filter(review -> java.util.Objects.equals(review.orderId(), orderId))
                    .findFirst();
        }

        @Override
        public Optional<OrderReviewRecord> findReviewByRequestId(Long shopId, String userId, String requestId) {
            return reviewsById.values().stream()
                    .filter(review -> java.util.Objects.equals(review.shopId(), shopId))
                    .filter(review -> java.util.Objects.equals(review.userId(), userId))
                    .filter(review -> java.util.Objects.equals(review.requestId(), requestId))
                    .findFirst();
        }

        @Override
        public Long createReview(OrderReviewRecord review) {
            Long reviewId = ++nextReviewId;
            OrderReviewRecord created = new OrderReviewRecord(
                    reviewId,
                    review.shopId(),
                    review.orderId(),
                    review.orderNo(),
                    review.userId(),
                    review.rating(),
                    review.content(),
                    review.imageUrls(),
                    review.requestId(),
                    review.traceId(),
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            reviewsById.put(reviewId, created);
            OrderSnapshot snapshot = snapshotsById.get(review.orderId());
            snapshotsById.put(review.orderId(), new OrderSnapshot(snapshot.order(), snapshot.items(), created));
            return reviewId;
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
            return 1L;
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            return 0;
        }

        @Override
        public void updateCompensationMetadata(Long shopId, Long orderId, String result, String errorCode, String reason, String traceId, String trigger, String operator, LocalDateTime compensatedAt) {
        }

        @Override
        public void appendCompensationAttempt(Long shopId, Long orderId, String orderNo, String reservationNo, String result, String errorCode, String reason, String traceId, String trigger, String operator) {
        }

        private Long seedOrder(Long shopId, String userId, String orderNo, OrderStatus status) {
            Long orderId = ++nextOrderId;
            snapshotsById.put(orderId, new OrderSnapshot(
                    new OrderRecord(
                            orderId,
                            shopId,
                            userId,
                            orderNo,
                            "req-" + orderId,
                            "ord:" + userId + ":req-" + orderId,
                            status,
                            59900L,
                            "trace-order",
                            LocalDateTime.now().minusDays(1),
                            LocalDateTime.now(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            status == OrderStatus.COMPLETED ? "completed" : null,
                            "SF Express",
                            "SF123",
                            LocalDateTime.now().minusHours(2),
                            "ship-001",
                            "trace-ship",
                            "receipt-001",
                            "trace-receipt",
                            status == OrderStatus.COMPLETED ? LocalDateTime.now().minusHours(1) : null
                    ),
                    List.of(new OrderItemRecord(1L, orderId, 301L, 401L, "Sneaker 42", 59900L, 1, 59900L))
            ));
            return orderId;
        }
    }
}
