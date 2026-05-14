package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.AdminOrderDetailResponse;
import com.sangui.shop.order.api.dto.AdminOrderPageResponse;
import com.sangui.shop.order.api.dto.AdminOrderSummaryResponse;
import com.sangui.shop.order.api.dto.AdminCancelOrderRequest;
import com.sangui.shop.order.client.InventoryReservationSnapshot;
import com.sangui.shop.order.client.InventoryReserveItemSnapshot;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.domain.AdminOrderQuery;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderItemDraft;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminOrderManagementServiceTest {

    private static final SanguiPrincipal ORDER_ADMIN = new SanguiPrincipal(
            "90001",
            1L,
            java.util.Set.of(),
            java.util.Set.of(SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN),
            "jwt-admin"
    );

    private InMemoryOrderRepository orderRepository;
    private StubProductCatalogClient productCatalogClient;
    private AdminOrderManagementService adminOrderManagementService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        productCatalogClient = new StubProductCatalogClient();
        OrderCancelService orderCancelService = new OrderCancelService(orderRepository, productCatalogClient);
        adminOrderManagementService = new AdminOrderManagementService(orderRepository, orderCancelService);
    }

    @Test
    void listOrdersFiltersInsideTrustedShopScope() {
        orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);
        orderRepository.seedOrder(1L, "10002", "ORD-002", "req-002", "ord:10002:req-002", OrderStatus.PAID, 129900L);
        orderRepository.seedOrder(2L, "10001", "ORD-003", "req-003", "ord:10001:req-003", OrderStatus.CREATED, 89900L);

        AdminOrderPageResponse response = adminOrderManagementService.listOrders(
                ORDER_ADMIN,
                1,
                20,
                "created",
                "ORD",
                "10001",
                null,
                null
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(AdminOrderSummaryResponse::orderNo).containsExactly("ORD-001");
        assertThat(response.items().get(0).itemCount()).isEqualTo(2);
        assertThat(response.items().get(0).paymentNo()).isNull();
        assertThat(response.items().get(0).traceId()).isEqualTo("trace-seed-ORD-001");
    }

    @Test
    void detailReturnsReservationTraceItemsAndDerivedTimeline() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.PAID, 59900L);

        AdminOrderDetailResponse response = adminOrderManagementService.getOrder(ORDER_ADMIN, orderId);

        assertThat(response.reservationNo()).isEqualTo("ord:10001:req-001");
        assertThat(response.paymentNo()).isNull();
        assertThat(response.traceId()).isEqualTo("trace-seed-ORD-001");
        assertThat(response.items()).hasSize(1);
        assertThat(response.statusTimeline()).extracting("status").containsExactly("created", "paid");
    }

    @Test
    void listAndDetailExposeCompletedMainStatusAndTimeline() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.COMPLETED, 59900L);
        orderRepository.seedOrder(1L, "10002", "ORD-002", "req-002", "ord:10002:req-002", OrderStatus.SHIPPED, 129900L);

        AdminOrderPageResponse listResponse = adminOrderManagementService.listOrders(
                ORDER_ADMIN,
                1,
                20,
                "completed",
                null,
                null,
                null,
                null
        );
        AdminOrderDetailResponse detailResponse = adminOrderManagementService.getOrder(ORDER_ADMIN, orderId);

        assertThat(listResponse.total()).isEqualTo(1);
        assertThat(listResponse.items()).extracting(AdminOrderSummaryResponse::status).containsExactly("completed");
        assertThat(detailResponse.status()).isEqualTo("completed");
        assertThat(detailResponse.statusTimeline()).extracting("status").containsExactly("created", "completed");
    }

    @Test
    void cancelCreatedOrderUsesSharedReleasePathWithoutUserOwnershipCheck() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);

        AdminOrderDetailResponse response = adminOrderManagementService.cancelOrder(
                ORDER_ADMIN,
                orderId,
                new AdminCancelOrderRequest("adm-cancel-001"),
                "trace-admin-cancel"
        );

        assertThat(response.status()).isEqualTo("cancelled");
        assertThat(productCatalogClient.releaseCalls).isEqualTo(1);
        assertThat(productCatalogClient.lastReservationNo).isEqualTo("ord:10001:req-001");
    }

    @Test
    void rejectsCompensationOnlyPermission() {
        SanguiPrincipal compensationOnly = new SanguiPrincipal(
                "90002",
                1L,
                java.util.Set.of(),
                java.util.Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN),
                "jwt-compensation"
        );

        assertThatThrownBy(() -> adminOrderManagementService.listOrders(compensationOnly, 1, 20, null, null, null, null, null))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void rejectsInvalidTimeRangeAsValidationFailure() {
        assertThatThrownBy(() -> adminOrderManagementService.listOrders(
                ORDER_ADMIN,
                1,
                20,
                null,
                null,
                null,
                LocalDateTime.parse("2026-05-02T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00")
        ))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(exception.httpStatus()).isEqualTo(400);
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
        public List<OrderSnapshot> findAdminSnapshots(AdminOrderQuery query, int offset, int limit) {
            return snapshotsById.values().stream()
                    .filter(snapshot -> java.util.Objects.equals(snapshot.order().shopId(), query.shopId()))
                    .filter(snapshot -> query.status() == null || snapshot.order().status() == query.status())
                    .filter(snapshot -> query.orderNo() == null || snapshot.order().orderNo().contains(query.orderNo()))
                    .filter(snapshot -> query.userId() == null || snapshot.order().userId().equals(query.userId()))
                    .filter(snapshot -> query.fromTime() == null || !snapshot.order().createdAt().isBefore(query.fromTime()))
                    .filter(snapshot -> query.toTime() == null || !snapshot.order().createdAt().isAfter(query.toTime()))
                    .sorted(Comparator.comparing((OrderSnapshot snapshot) -> snapshot.order().createdAt()).reversed()
                            .thenComparing(snapshot -> snapshot.order().id(), Comparator.reverseOrder()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countAdminOrders(AdminOrderQuery query) {
            return findAdminSnapshots(query, 0, Integer.MAX_VALUE).size();
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
            snapshotsById.put(orderId, snapshot(orderId, shopId, userId, orderNo, draft.requestId(), draft.reservationNo(), status, draft.totalAmountCent()));
            return orderId;
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || !snapshot.order().shopId().equals(shopId) || snapshot.order().status() != currentStatus) {
                return 0;
            }
            snapshotsById.put(orderId, snapshot(
                    orderId,
                    snapshot.order().shopId(),
                    snapshot.order().userId(),
                    snapshot.order().orderNo(),
                    snapshot.order().requestId(),
                    snapshot.order().reservationNo(),
                    nextStatus,
                    snapshot.order().totalAmountCent()
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
            snapshotsById.put(orderId, snapshot(orderId, shopId, userId, orderNo, requestId, reservationNo, status, totalAmountCent));
            return orderId;
        }

        private OrderSnapshot snapshot(
                Long orderId,
                Long shopId,
                String userId,
                String orderNo,
                String requestId,
                String reservationNo,
                OrderStatus status,
                Long totalAmountCent
        ) {
            LocalDateTime createdAt = LocalDateTime.parse("2026-05-01T10:00:00").plusSeconds(orderId);
            return new OrderSnapshot(
                    new OrderRecord(
                            orderId,
                            shopId,
                            userId,
                            orderNo,
                            requestId,
                            reservationNo,
                            status,
                            totalAmountCent,
                            "trace-seed-" + orderNo,
                            createdAt,
                            status == OrderStatus.CREATED ? createdAt : createdAt.plusMinutes(5),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    ),
                    List.of(new OrderItemRecord(1L, orderId, 301L, 401L, "Sneaker 42", 29950L, 2, 59900L))
            );
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
