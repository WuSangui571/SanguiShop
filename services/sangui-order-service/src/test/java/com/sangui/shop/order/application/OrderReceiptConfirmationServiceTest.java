package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.ConfirmOrderReceiptRequest;
import com.sangui.shop.order.api.dto.OrderResponse;
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

class OrderReceiptConfirmationServiceTest {

    private static final SanguiPrincipal USER_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("USER"),
            java.util.Set.of("order:write"),
            "jwt-user"
    );

    private InMemoryOrderRepository orderRepository;
    private OrderReceiptConfirmationService orderReceiptConfirmationService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderReceiptConfirmationService = new OrderReceiptConfirmationService(orderRepository);
    }

    @Test
    void confirmReceiptUpdatesShippedOrderToCompleted() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.SHIPPED);

        OrderResponse response = orderReceiptConfirmationService.confirmReceipt(
                USER_PRINCIPAL,
                orderId,
                new ConfirmOrderReceiptRequest(" receipt-001 "),
                "trace-receipt"
        );

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.fulfillmentStatus()).isEqualTo("completed");
        assertThat(response.completedAt()).isNotNull();
        OrderRecord updated = orderRepository.findById(1L, orderId).orElseThrow();
        assertThat(updated.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(updated.receiptRequestId()).isEqualTo("receipt-001");
        assertThat(updated.receiptTraceId()).isEqualTo("trace-receipt");
        assertThat(updated.carrier()).isEqualTo("SF Express");
        assertThat(updated.trackingNo()).isEqualTo("SF123");
    }

    @Test
    void confirmReceiptIsIdempotentForCompletedOrder() {
        Long orderId = orderRepository.seedCompletedOrder(1L, "10001", "ORD-001", "receipt-001");

        OrderResponse response = orderReceiptConfirmationService.confirmReceipt(
                USER_PRINCIPAL,
                orderId,
                new ConfirmOrderReceiptRequest("receipt-replay"),
                "trace-replay"
        );

        assertThat(response.status()).isEqualTo("completed");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().receiptRequestId()).isEqualTo("receipt-001");
    }

    @Test
    void confirmReceiptRejectsUnshippedAndCancelledOrders() {
        for (OrderStatus status : List.of(OrderStatus.CREATED, OrderStatus.PAID, OrderStatus.CANCELLED)) {
            Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-" + status.value(), status);

            assertThatThrownBy(() -> orderReceiptConfirmationService.confirmReceipt(
                    USER_PRINCIPAL,
                    orderId,
                    new ConfirmOrderReceiptRequest("receipt-001"),
                    "trace-receipt"
            )).isInstanceOfSatisfying(SanguiException.class, exception -> {
                assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID.code());
                assertThat(exception.httpStatus()).isEqualTo(409);
            });
        }
    }

    @Test
    void confirmReceiptUsesPrincipalOwnershipScope() {
        Long orderId = orderRepository.seedOrder(1L, "20002", "ORD-OTHER", OrderStatus.SHIPPED);

        assertThatThrownBy(() -> orderReceiptConfirmationService.confirmReceipt(
                USER_PRINCIPAL,
                orderId,
                new ConfirmOrderReceiptRequest("receipt-001"),
                "trace-receipt"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.code());
            assertThat(exception.httpStatus()).isEqualTo(404);
        });
    }

    @Test
    void confirmReceiptRejectsBlankRequestId() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.SHIPPED);

        assertThatThrownBy(() -> orderReceiptConfirmationService.confirmReceipt(
                USER_PRINCIPAL,
                orderId,
                new ConfirmOrderReceiptRequest(" "),
                "trace-receipt"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
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
            return 1L;
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            return 0;
        }

        @Override
        public int markCompleted(Long shopId, Long orderId, String requestId, String traceId, LocalDateTime completedAt) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null
                    || !java.util.Objects.equals(snapshot.order().shopId(), shopId)
                    || snapshot.order().status() != OrderStatus.SHIPPED) {
                return 0;
            }
            put(
                    orderId,
                    shopId,
                    snapshot.order().userId(),
                    snapshot.order().orderNo(),
                    OrderStatus.COMPLETED,
                    snapshot.order().carrier(),
                    snapshot.order().trackingNo(),
                    snapshot.order().shippedAt(),
                    snapshot.order().shipmentRequestId(),
                    snapshot.order().shipmentTraceId(),
                    requestId,
                    traceId,
                    completedAt
            );
            return 1;
        }

        @Override
        public void updateCompensationMetadata(Long shopId, Long orderId, String result, String errorCode, String reason, String traceId, String trigger, String operator, LocalDateTime compensatedAt) {
        }

        @Override
        public void appendCompensationAttempt(Long shopId, Long orderId, String orderNo, String reservationNo, String result, String errorCode, String reason, String traceId, String trigger, String operator) {
        }

        private Long seedOrder(Long shopId, String userId, String orderNo, OrderStatus status) {
            Long orderId = ++nextOrderId;
            LocalDateTime shippedAt = status == OrderStatus.SHIPPED ? LocalDateTime.now().minusHours(1) : null;
            put(orderId, shopId, userId, orderNo, status, "SF Express", "SF123", shippedAt, "ship-001", "trace-ship", null, null, null);
            return orderId;
        }

        private Long seedCompletedOrder(Long shopId, String userId, String orderNo, String requestId) {
            Long orderId = ++nextOrderId;
            put(
                    orderId,
                    shopId,
                    userId,
                    orderNo,
                    OrderStatus.COMPLETED,
                    "SF Express",
                    "SF123",
                    LocalDateTime.now().minusHours(2),
                    "ship-001",
                    "trace-ship",
                    requestId,
                    "trace-receipt",
                    LocalDateTime.now().minusHours(1)
            );
            return orderId;
        }

        private void put(
                Long orderId,
                Long shopId,
                String userId,
                String orderNo,
                OrderStatus status,
                String carrier,
                String trackingNo,
                LocalDateTime shippedAt,
                String shipmentRequestId,
                String shipmentTraceId,
                String receiptRequestId,
                String receiptTraceId,
                LocalDateTime completedAt
        ) {
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
                            status == OrderStatus.COMPLETED ? "completed" : status == OrderStatus.SHIPPED ? "shipped" : null,
                            carrier,
                            trackingNo,
                            shippedAt,
                            shipmentRequestId,
                            shipmentTraceId,
                            receiptRequestId,
                            receiptTraceId,
                            completedAt
                    ),
                    List.of(new OrderItemRecord(1L, orderId, 301L, 401L, "Sneaker 42", 59900L, 1, 59900L))
            ));
        }
    }
}
