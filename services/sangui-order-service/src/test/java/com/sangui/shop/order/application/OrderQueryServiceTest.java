package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderQueryServiceTest {

    private static final SanguiPrincipal USER_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("USER"),
            java.util.Set.of("order:read"),
            "jwt-user"
    );

    private InMemoryOrderRepository orderRepository;
    private OrderQueryService orderQueryService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderQueryService = new OrderQueryService(orderRepository);
    }

    @Test
    void getOrderReturnsOwnedOrderSnapshotWithItemsAndTimestamps() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-101", OrderStatus.CREATED);

        var response = orderQueryService.getOrder(USER_PRINCIPAL, orderId);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.userId()).isEqualTo("10001");
        assertThat(response.status()).isEqualTo("created");
        assertThat(response.items()).hasSize(1);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getOrderReturnsNotFoundForMissingOrder() {
        assertThatThrownBy(() -> orderQueryService.getOrder(USER_PRINCIPAL, 404L))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.code());
                    assertThat(exception.httpStatus()).isEqualTo(404);
                });
    }

    @Test
    void getOrderReturnsNotFoundForDifferentOwner() {
        Long orderId = orderRepository.seedOrder(1L, "20002", "ORD-202", OrderStatus.CREATED);

        assertThatThrownBy(() -> orderQueryService.getOrder(USER_PRINCIPAL, orderId))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.code());
                    assertThat(exception.httpStatus()).isEqualTo(404);
                });
    }

    @Test
    void listOrdersOnlyReturnsCurrentPrincipalOrders() {
        orderRepository.seedOrder(1L, "10001", "ORD-101", OrderStatus.CREATED);
        orderRepository.seedOrder(1L, "20002", "ORD-202", OrderStatus.PAID);

        var response = orderQueryService.listOrders(USER_PRINCIPAL, 1, 10);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(order -> {
            assertThat(order.orderNo()).isEqualTo("ORD-101");
            assertThat(order.userId()).isEqualTo("10001");
        });
    }

    @Test
    void getOrderReturnsCompletedFulfillmentSnapshot() {
        Long orderId = orderRepository.seedCompletedOrder(1L, "10001", "ORD-303");

        var response = orderQueryService.getOrder(USER_PRINCIPAL, orderId);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.fulfillmentStatus()).isEqualTo("completed");
        assertThat(response.completedAt()).isNotNull();
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<Long, OrderSnapshot> snapshotsById = new LinkedHashMap<>();
        private long nextOrderId = 10000L;

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
        public List<OrderSnapshot> findSnapshotsByUser(Long shopId, String userId, int offset, int limit) {
            return snapshotsById.values().stream()
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().shopId(), shopId))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().userId(), userId))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countByUser(Long shopId, String userId) {
            return snapshotsById.values().stream()
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().shopId(), shopId))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().userId(), userId))
                    .count();
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
            return null;
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
                            "trace-seed",
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    ),
                    List.of(new OrderItemRecord(1L, orderId, 301L, 401L, "Sneaker 42", 59900L, 1, 59900L))
            ));
            return orderId;
        }

        private Long seedCompletedOrder(Long shopId, String userId, String orderNo) {
            Long orderId = ++nextOrderId;
            snapshotsById.put(orderId, new OrderSnapshot(
                    new OrderRecord(
                            orderId,
                            shopId,
                            userId,
                            orderNo,
                            "req-" + orderId,
                            "ord:" + userId + ":req-" + orderId,
                            OrderStatus.COMPLETED,
                            59900L,
                            "trace-seed",
                            LocalDateTime.now().minusDays(1),
                            LocalDateTime.now(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "completed",
                            "SF Express",
                            "SF123",
                            LocalDateTime.now().minusHours(2),
                            "ship-001",
                            "trace-ship",
                            "receipt-001",
                            "trace-receipt",
                            LocalDateTime.now().minusHours(1)
                    ),
                    List.of(new OrderItemRecord(1L, orderId, 301L, 401L, "Sneaker 42", 59900L, 1, 59900L))
            ));
            return orderId;
        }
    }
}
