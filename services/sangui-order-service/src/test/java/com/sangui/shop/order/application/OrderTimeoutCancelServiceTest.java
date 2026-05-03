package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.order.client.InventoryReservationSnapshot;
import com.sangui.shop.order.client.InventoryReserveItemSnapshot;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersRequest;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderItemDraft;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderTimeoutCancelServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-01T14:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    private InMemoryOrderRepository orderRepository;
    private StubProductCatalogClient productCatalogClient;
    private OrderTimeoutCancelService orderTimeoutCancelService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        productCatalogClient = new StubProductCatalogClient();
        orderTimeoutCancelService = new OrderTimeoutCancelService(orderRepository, productCatalogClient, FIXED_CLOCK);
    }

    @Test
    void cancelExpiredOrdersReleasesInventoryAndMarksCreatedOrderCancelled() {
        Long orderId = orderRepository.seedOrder(
                1L,
                "10001",
                "ORD-001",
                "req-001",
                "ord:10001:req-001",
                OrderStatus.CREATED,
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(20)
        );

        CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(
                new CancelExpiredOrdersRequest(1L, 15, 100),
                "trace-timeout"
        );

        assertThat(response.scannedCount()).isEqualTo(1);
        assertThat(response.cancelledCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(productCatalogClient.releaseCalls).isEqualTo(1);
        assertThat(productCatalogClient.lastReservationNo).isEqualTo("ord:10001:req-001");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void duplicateTimeoutCancelDoesNotReleaseInventoryTwice() {
        Long orderId = orderRepository.seedOrder(
                1L,
                "10001",
                "ORD-001",
                "req-001",
                "ord:10001:req-001",
                OrderStatus.CREATED,
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(20)
        );

        CancelExpiredOrdersRequest request = new CancelExpiredOrdersRequest(1L, 15, 100);
        orderTimeoutCancelService.cancelExpiredOrders(request, "trace-timeout-1");
        CancelExpiredOrdersResponse replay = orderTimeoutCancelService.cancelExpiredOrders(request, "trace-timeout-2");

        assertThat(replay.scannedCount()).isZero();
        assertThat(replay.cancelledCount()).isZero();
        assertThat(replay.failedCount()).isZero();
        assertThat(productCatalogClient.releaseCalls).isEqualTo(1);
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paidOrderIsSkippedWhenCallbackWinsBeforeTimeout() {
        Long orderId = orderRepository.seedOrder(
                1L,
                "10001",
                "ORD-001",
                "req-001",
                "ord:10001:req-001",
                OrderStatus.PAID,
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(20)
        );

        CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(
                new CancelExpiredOrdersRequest(1L, 15, 100),
                "trace-timeout-paid"
        );

        assertThat(response.scannedCount()).isZero();
        assertThat(response.failedCount()).isZero();
        assertThat(productCatalogClient.releaseCalls).isZero();
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void cancelExpiredOrdersContinuesWhenOneReleaseFails() {
        orderRepository.seedOrder(
                1L,
                "10001",
                "ORD-001",
                "req-001",
                "ord:10001:req-001",
                OrderStatus.CREATED,
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(20)
        );
        Long secondOrderId = orderRepository.seedOrder(
                1L,
                "10002",
                "ORD-002",
                "req-002",
                "ord:10002:req-002",
                OrderStatus.CREATED,
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(30)
        );
        productCatalogClient.failReservationNo = "ord:10001:req-001";

        CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(
                new CancelExpiredOrdersRequest(1L, 15, 100),
                "trace-timeout-partial-failure"
        );

        assertThat(response.scannedCount()).isEqualTo(2);
        assertThat(response.cancelledCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(orderRepository.findById(1L, secondOrderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void manualReplaySkipsOrderThatIsNotYetTimedOut() {
        Long orderId = orderRepository.seedOrder(
                1L,
                "10001",
                "ORD-003",
                "req-003",
                "ord:10001:req-003",
                OrderStatus.CREATED,
                LocalDateTime.now(FIXED_CLOCK).minusMinutes(5)
        );

        OrderTimeoutReplayExecution execution = orderTimeoutCancelService.replayTimeoutOrder(
                1L,
                orderId,
                15,
                "trace-manual-order",
                "manual"
        );

        assertThat(execution.result()).isEqualTo("skipped");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().lastCompensationResult()).isEqualTo("skipped");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().lastCompensationTrigger()).isEqualTo("manual");
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final AtomicLong nextOrderId = new AtomicLong(10000);
        private final Map<Long, OrderSnapshot> snapshotsById = new LinkedHashMap<>();
        private final Map<Long, LocalDateTime> createdAtById = new LinkedHashMap<>();

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
            return snapshotsById.values().stream()
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().shopId(), shopId))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().userId(), userId))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().requestId(), requestId))
                    .findFirst();
        }

        @Override
        public List<OrderRecord> findExpiredCreatedOrders(Long shopId, LocalDateTime createdBefore, int limit) {
            return snapshotsById.values().stream()
                    .map(OrderSnapshot::order)
                    .filter(order -> java.util.Objects.equals(order.shopId(), shopId))
                    .filter(order -> order.status() == OrderStatus.CREATED)
                    .filter(order -> !createdAtById.get(order.id()).isAfter(createdBefore))
                    .sorted(Comparator.comparing(OrderRecord::id))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<OrderRecord> findCancelledOrders(Long shopId, int limit) {
            return snapshotsById.values().stream()
                    .map(OrderSnapshot::order)
                    .filter(order -> java.util.Objects.equals(order.shopId(), shopId))
                    .filter(order -> order.status() == OrderStatus.CANCELLED)
                    .sorted(Comparator.comparing(OrderRecord::updatedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft) {
            Long orderId = nextOrderId.incrementAndGet();
            put(orderId, shopId, userId, orderNo, draft.requestId(), draft.reservationNo(), status, traceId, LocalDateTime.now(FIXED_CLOCK));
            return orderId;
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || snapshot.order().status() != currentStatus) {
                return 0;
            }
            put(
                    orderId,
                    snapshot.order().shopId(),
                    snapshot.order().userId(),
                    snapshot.order().orderNo(),
                    snapshot.order().requestId(),
                    snapshot.order().reservationNo(),
                    nextStatus,
                    snapshot.order().traceId(),
                    createdAtById.get(orderId),
                    snapshot.order().lastCompensationResult(),
                    snapshot.order().lastCompensationErrorCode(),
                    snapshot.order().lastCompensationReason(),
                    snapshot.order().lastCompensationTraceId(),
                    snapshot.order().lastCompensationTrigger(),
                    snapshot.order().lastCompensatedAt()
            );
            return 1;
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
                LocalDateTime compensatedAt
        ) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || !java.util.Objects.equals(snapshot.order().shopId(), shopId)) {
                return;
            }
            put(
                    orderId,
                    snapshot.order().shopId(),
                    snapshot.order().userId(),
                    snapshot.order().orderNo(),
                    snapshot.order().requestId(),
                    snapshot.order().reservationNo(),
                    snapshot.order().status(),
                    snapshot.order().traceId(),
                    createdAtById.get(orderId),
                    result,
                    errorCode,
                    reason,
                    traceId,
                    trigger,
                    compensatedAt
            );
        }

        private Long seedOrder(
                Long shopId,
                String userId,
                String orderNo,
                String requestId,
                String reservationNo,
                OrderStatus status,
                LocalDateTime createdAt
        ) {
            Long orderId = nextOrderId.incrementAndGet();
            put(orderId, shopId, userId, orderNo, requestId, reservationNo, status, "trace-seed", createdAt);
            return orderId;
        }

        private void put(
                Long orderId,
                Long shopId,
                String userId,
                String orderNo,
                String requestId,
                String reservationNo,
                OrderStatus status,
                String traceId,
                LocalDateTime createdAt
        ) {
            put(orderId, shopId, userId, orderNo, requestId, reservationNo, status, traceId, createdAt, null, null, null, null, null, null);
        }

        private void put(
                Long orderId,
                Long shopId,
                String userId,
                String orderNo,
                String requestId,
                String reservationNo,
                OrderStatus status,
                String traceId,
                LocalDateTime createdAt,
                String lastCompensationResult,
                String lastCompensationErrorCode,
                String lastCompensationReason,
                String lastCompensationTraceId,
                String lastCompensationTrigger,
                LocalDateTime lastCompensatedAt
        ) {
            snapshotsById.put(orderId, new OrderSnapshot(
                    new OrderRecord(
                            orderId,
                            shopId,
                            userId,
                            orderNo,
                            requestId,
                            reservationNo,
                            status,
                            59900L,
                            traceId,
                            createdAt,
                            createdAt,
                            lastCompensationResult,
                            lastCompensationErrorCode,
                            lastCompensationReason,
                            lastCompensationTraceId,
                            lastCompensationTrigger,
                            lastCompensatedAt
                    ),
                    List.of(new OrderItemRecord(1L, orderId, 301L, 401L, "Sneaker 42", 59900L, 1, 59900L))
            ));
            createdAtById.put(orderId, createdAt);
        }
    }

    private static final class StubProductCatalogClient implements ProductCatalogClient {

        private int releaseCalls;
        private String lastReservationNo;
        private String failReservationNo;

        @Override
        public InventoryReservationSnapshot reserveInventory(Long shopId, String reservationNo, List<InventoryReserveItemSnapshot> items, String traceId) {
            return new InventoryReservationSnapshot(reservationNo, "reserved", List.of());
        }

        @Override
        public InventoryReservationSnapshot releaseInventory(Long shopId, String reservationNo, String traceId) {
            if (java.util.Objects.equals(failReservationNo, reservationNo)) {
                throw new IllegalStateException("simulated release failure");
            }
            releaseCalls++;
            lastReservationNo = reservationNo;
            return new InventoryReservationSnapshot(reservationNo, "released", List.of());
        }
    }
}
