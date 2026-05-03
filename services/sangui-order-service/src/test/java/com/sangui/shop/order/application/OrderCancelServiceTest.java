package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.client.InventoryReservationSnapshot;
import com.sangui.shop.order.client.InventoryReserveItemSnapshot;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderItemDraft;
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

class OrderCancelServiceTest {

    private static final SanguiPrincipal USER_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("USER"),
            java.util.Set.of("order:create"),
            "jwt-user"
    );

    private InMemoryOrderRepository orderRepository;
    private StubProductCatalogClient productCatalogClient;
    private OrderCancelService orderCancelService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        productCatalogClient = new StubProductCatalogClient();
        orderCancelService = new OrderCancelService(orderRepository, productCatalogClient);
    }

    @Test
    void cancelOrderReleasesInventoryAndMarksOrderCancelled() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);

        OrderResponse response = orderCancelService.cancelOrder(USER_PRINCIPAL, orderId, "trace-cancel");

        assertThat(response.status()).isEqualTo("cancelled");
        assertThat(productCatalogClient.releaseCalls).isEqualTo(1);
        assertThat(productCatalogClient.lastReservationNo).isEqualTo("ord:10001:req-001");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrderRejectsPaidOrder() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.PAID, 59900L);

        assertThatThrownBy(() -> orderCancelService.cancelOrder(USER_PRINCIPAL, orderId, "trace-cancel"))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID.code());
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
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
            return snapshotsById.values().stream()
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().shopId(), shopId))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().userId(), userId))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().requestId(), requestId))
                    .findFirst();
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
            Long orderId = ++nextOrderId;
            snapshotsById.put(orderId, new OrderSnapshot(
                    new OrderRecord(
                            orderId,
                            shopId,
                            userId,
                            orderNo,
                            draft.requestId(),
                            draft.reservationNo(),
                            status,
                            draft.totalAmountCent(),
                            traceId,
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

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || snapshot.order().status() != currentStatus) {
                return 0;
            }
            snapshotsById.put(orderId, new OrderSnapshot(
                    new OrderRecord(
                            snapshot.order().id(),
                            snapshot.order().shopId(),
                            snapshot.order().userId(),
                            snapshot.order().orderNo(),
                            snapshot.order().requestId(),
                            snapshot.order().reservationNo(),
                    nextStatus,
                    snapshot.order().totalAmountCent(),
                    snapshot.order().traceId(),
                    snapshot.order().createdAt(),
                    LocalDateTime.now(),
                    snapshot.order().lastCompensationResult(),
                    snapshot.order().lastCompensationErrorCode(),
                    snapshot.order().lastCompensationReason(),
                    snapshot.order().lastCompensationTraceId(),
                    snapshot.order().lastCompensationTrigger(),
                    snapshot.order().lastCompensationOperator(),
                    snapshot.order().lastCompensatedAt()
                    ),
                    snapshot.items()
            ));
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

        private Long seedOrder(
                Long shopId,
                String userId,
                String orderNo,
                String requestId,
                String reservationNo,
                OrderStatus status,
                Long totalAmountCent
        ) {
            Long orderId = ++nextOrderId;
            snapshotsById.put(orderId, new OrderSnapshot(
                    new OrderRecord(
                            orderId,
                            shopId,
                            userId,
                            orderNo,
                            requestId,
                            reservationNo,
                            status,
                            totalAmountCent,
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
    }

    private static final class StubProductCatalogClient implements ProductCatalogClient {

        private int releaseCalls;
        private String lastReservationNo;

        @Override
        public InventoryReservationSnapshot reserveInventory(Long shopId, String reservationNo, List<InventoryReserveItemSnapshot> items, String traceId) {
            return new InventoryReservationSnapshot(reservationNo, "reserved", List.of());
        }

        @Override
        public InventoryReservationSnapshot releaseInventory(Long shopId, String reservationNo, String traceId) {
            releaseCalls++;
            lastReservationNo = reservationNo;
            return new InventoryReservationSnapshot(reservationNo, "released", List.of());
        }
    }
}
