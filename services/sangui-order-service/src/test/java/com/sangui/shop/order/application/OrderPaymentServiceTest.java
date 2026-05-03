package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.dto.ConfirmOrderPaymentRequest;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotRequest;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotResponse;
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

class OrderPaymentServiceTest {

    private InMemoryOrderRepository orderRepository;
    private OrderPaymentService orderPaymentService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderPaymentService = new OrderPaymentService(orderRepository);
    }

    @Test
    void getPayableOrderReturnsCreatedOrderForMatchingUser() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);

        OrderPaymentSnapshotResponse response = orderPaymentService.getPayableOrder(
                new OrderPaymentSnapshotRequest(1L, "10001", orderId)
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderNo()).isEqualTo("ORD-001");
        assertThat(response.reservationNo()).isEqualTo("ord:10001:req-001");
        assertThat(response.status()).isEqualTo("created");
        assertThat(response.totalAmountCent()).isEqualTo(59900L);
    }

    @Test
    void confirmPaidUpdatesCreatedOrderToPaid() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);

        OrderPaymentSnapshotResponse response = orderPaymentService.confirmPaid(
                new ConfirmOrderPaymentRequest(1L, "10001", orderId, "PAY-001", 59900L)
        );

        assertThat(response.status()).isEqualTo("paid");
        assertThat(orderRepository.findById(1L, orderId).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void confirmPaidIsIdempotentForAlreadyPaidOrder() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.PAID, 59900L);

        OrderPaymentSnapshotResponse response = orderPaymentService.confirmPaid(
                new ConfirmOrderPaymentRequest(1L, "10001", orderId, "PAY-001", 59900L)
        );

        assertThat(response.status()).isEqualTo("paid");
    }

    @Test
    void confirmPaidRejectsAmountMismatch() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);

        assertThatThrownBy(() -> orderPaymentService.confirmPaid(
                new ConfirmOrderPaymentRequest(1L, "10001", orderId, "PAY-001", 60000L)
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH.code());
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void getPayableOrderRejectsWrongOwner() {
        Long orderId = orderRepository.seedOrder(1L, "10001", "ORD-001", "req-001", "ord:10001:req-001", OrderStatus.CREATED, 59900L);

        assertThatThrownBy(() -> orderPaymentService.getPayableOrder(
                new OrderPaymentSnapshotRequest(1L, "10002", orderId)
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND.code());
            assertThat(exception.httpStatus()).isEqualTo(404);
        });
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final AtomicLong nextOrderId = new AtomicLong(10000);
        private final Map<Long, OrderSnapshot> snapshotsById = new LinkedHashMap<>();

        private Long seedOrder(
                Long shopId,
                String userId,
                String orderNo,
                String requestId,
                String reservationNo,
                OrderStatus status,
                Long totalAmountCent
        ) {
            Long orderId = nextOrderId.incrementAndGet();
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
                            null
                    ),
                    List.of(new com.sangui.shop.order.domain.OrderItemRecord(
                            1L,
                            orderId,
                            301L,
                            401L,
                            "Sneaker 42",
                            totalAmountCent,
                            1,
                            totalAmountCent
                    ))
            ));
            return orderId;
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
            Long orderId = nextOrderId.incrementAndGet();
            List<com.sangui.shop.order.domain.OrderItemRecord> items = draft.items().stream()
                    .map(item -> new com.sangui.shop.order.domain.OrderItemRecord(
                            1L,
                            orderId,
                            item.productId(),
                            item.skuId(),
                            item.skuName(),
                            item.priceCent(),
                            item.quantity(),
                            item.lineAmountCent()
                    ))
                    .toList();
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
                            null
                    ),
                    items
            ));
            return orderId;
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            OrderSnapshot snapshot = snapshotsById.get(orderId);
            if (snapshot == null || !java.util.Objects.equals(snapshot.order().shopId(), shopId)) {
                return 0;
            }
            if (snapshot.order().status() != currentStatus) {
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
                LocalDateTime compensatedAt
        ) {
        }
    }
}
