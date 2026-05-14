package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.dto.ConfirmOrderShipmentRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderDetailRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderResponse;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderItemDraft;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderShipmentServiceTest {

    private InMemoryOrderRepository orderRepository;
    private OrderShipmentService orderShipmentService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderShipmentService = new OrderShipmentService(orderRepository);
    }

    @Test
    void confirmShipmentUpdatesPaidOrderToShipped() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.PAID);

        FulfillmentOrderResponse response = orderShipmentService.confirmShipment(new ConfirmOrderShipmentRequest(
                1L,
                orderId,
                " ship-001 ",
                " SF Express ",
                " SF123 "
        ), "trace-ship");

        assertThat(response.status()).isEqualTo("shipped");
        assertThat(response.fulfillmentStatus()).isEqualTo("shipped");
        assertThat(response.carrier()).isEqualTo("SF Express");
        assertThat(response.trackingNo()).isEqualTo("SF123");
        OrderRecord updated = orderRepository.findById(1L, orderId).orElseThrow();
        assertThat(updated.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(updated.shipmentRequestId()).isEqualTo("ship-001");
        assertThat(updated.shipmentTraceId()).isEqualTo("trace-ship");
    }

    @Test
    void confirmShipmentRejectsCreatedOrder() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.CREATED);

        assertThatThrownBy(() -> orderShipmentService.confirmShipment(new ConfirmOrderShipmentRequest(
                1L,
                orderId,
                "ship-001",
                "SF Express",
                "SF123"
        ), "trace-ship")).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID.code());
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void confirmShipmentIsIdempotentForSameRequestAndPayload() {
        Long orderId = orderRepository.seedShippedOrder(1L, "10001", "ORD-001", "ship-001", "SF Express", "SF123");

        FulfillmentOrderResponse response = orderShipmentService.confirmShipment(new ConfirmOrderShipmentRequest(
                1L,
                orderId,
                "ship-001",
                "SF Express",
                "SF123"
        ), "trace-replay");

        assertThat(response.status()).isEqualTo("shipped");
        assertThat(response.trackingNo()).isEqualTo("SF123");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().shipmentTraceId()).isEqualTo("trace-seed");
    }

    @Test
    void confirmShipmentRejectsDifferentPayloadForShippedOrder() {
        Long orderId = orderRepository.seedShippedOrder(1L, "10001", "ORD-001", "ship-001", "SF Express", "SF123");

        assertThatThrownBy(() -> orderShipmentService.confirmShipment(new ConfirmOrderShipmentRequest(
                1L,
                orderId,
                "ship-001",
                "YTO",
                "YT999"
        ), "trace-replay")).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(CommonErrorCode.IDEMPOTENCY_CONFLICT.code());
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void confirmShipmentUsesShopScope() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", OrderStatus.PAID);

        assertThatThrownBy(() -> orderShipmentService.confirmShipment(new ConfirmOrderShipmentRequest(
                2L,
                orderId,
                "ship-001",
                "SF Express",
                "SF123"
        ), "trace-ship")).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.code());
            assertThat(exception.httpStatus()).isEqualTo(404);
        });
    }

    @Test
    void detailPreservesCompletedMainStatusWhenFulfillmentIsShipped() {
        Long orderId = orderRepository.seedCompletedWithFulfillment(1L, "10001", "ORD-001", "shipped", "SF Express", "SF123", "ship-001", "trace-ship");

        FulfillmentOrderResponse response = orderShipmentService.getFulfillmentRecord(
                new FulfillmentOrderDetailRequest(1L, orderId)
        );

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.fulfillmentStatus()).isEqualTo("shipped");
        assertThat(response.carrier()).isEqualTo("SF Express");
        assertThat(response.trackingNo()).isEqualTo("SF123");
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void fulfillmentResponsePreservesMainStatusForEveryKnownStatus(OrderStatus status) {
        Long orderId = status == OrderStatus.SHIPPED
                ? orderRepository.seedShippedOrder(1L, "10001", "ORD-" + status.value(), "ship-req-001", "SF", "SF-TRACK")
                : status == OrderStatus.COMPLETED
                        ? orderRepository.seedCompletedWithFulfillment(1L, "10001", "ORD-" + status.value(), "shipped", "SF", "SF-TRACK", "ship-req", "trace-ship")
                        : orderRepository.seedOrder(1L, "10001", "ORD-" + status.value(), status);

        FulfillmentOrderResponse response = orderShipmentService.getFulfillmentRecord(
                new FulfillmentOrderDetailRequest(1L, orderId)
        );

        assertThat(response.status()).isEqualTo(status.value());
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final AtomicLong nextOrderId = new AtomicLong(10000);
        private final Map<Long, OrderSnapshot> snapshotsById = new LinkedHashMap<>();

        private Long seedOrder(Long shopId, String userId, String orderNo, OrderStatus status) {
            Long orderId = nextOrderId.incrementAndGet();
            put(orderId, shopId, userId, orderNo, status, null, null, null, null, null, null);
            return orderId;
        }

        private Long seedCompletedWithFulfillment(Long shopId, String userId, String orderNo, String fulfillmentStatus, String carrier, String trackingNo, String shipmentRequestId, String shipmentTraceId) {
            Long orderId = nextOrderId.incrementAndGet();
            put(orderId, shopId, userId, orderNo, OrderStatus.COMPLETED, fulfillmentStatus, carrier, trackingNo, LocalDateTime.now().minusDays(1), shipmentRequestId, shipmentTraceId);
            return orderId;
        }

        private Long seedShippedOrder(Long shopId, String userId, String orderNo, String requestId, String carrier, String trackingNo) {
            Long orderId = nextOrderId.incrementAndGet();
            put(orderId, shopId, userId, orderNo, OrderStatus.SHIPPED, "shipped", carrier, trackingNo, LocalDateTime.now(), requestId, "trace-seed");
            return orderId;
        }

        private void put(
                Long orderId,
                Long shopId,
                String userId,
                String orderNo,
                OrderStatus status,
                String fulfillmentStatus,
                String carrier,
                String trackingNo,
                LocalDateTime shippedAt,
                String shipmentRequestId,
                String shipmentTraceId
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
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            fulfillmentStatus,
                            carrier,
                            trackingNo,
                            shippedAt,
                            shipmentRequestId,
                            shipmentTraceId
                    ),
                    List.of()
            ));
        }

        @Override
        public Optional<OrderRecord> findById(Long shopId, Long orderId) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || !java.util.Objects.equals(snapshot.order().shopId(), shopId)) {
                return Optional.empty();
            }
            return Optional.of(snapshot.order());
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
        public int markShipped(Long shopId, Long orderId, String requestId, String carrier, String trackingNo, String traceId, LocalDateTime shippedAt) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || !java.util.Objects.equals(snapshot.order().shopId(), shopId) || snapshot.order().status() != OrderStatus.PAID) {
                return 0;
            }
            put(orderId, shopId, snapshot.order().userId(), snapshot.order().orderNo(), OrderStatus.SHIPPED, "shipped", carrier, trackingNo, shippedAt, requestId, traceId);
            return 1;
        }

        @Override
        public void updateCompensationMetadata(Long shopId, Long orderId, String result, String errorCode, String reason, String traceId, String trigger, String operator, LocalDateTime compensatedAt) {
        }

        @Override
        public void appendCompensationAttempt(Long shopId, Long orderId, String orderNo, String reservationNo, String result, String errorCode, String reason, String traceId, String trigger, String operator) {
        }
    }
}
