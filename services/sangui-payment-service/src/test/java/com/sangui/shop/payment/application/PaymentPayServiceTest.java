package com.sangui.shop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.payment.api.dto.CreatePaymentRequest;
import com.sangui.shop.payment.api.dto.PaymentResponse;
import com.sangui.shop.payment.client.OrderPaymentClient;
import com.sangui.shop.payment.client.OrderPaymentSnapshot;
import com.sangui.shop.payment.client.ProductInventoryClient;
import com.sangui.shop.payment.domain.PaymentCallbackLogDraft;
import com.sangui.shop.payment.domain.PaymentCallbackLogRecord;
import com.sangui.shop.payment.domain.PaymentCreateDraft;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentPayServiceTest {

    private static final SanguiPrincipal USER_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("USER"),
            java.util.Set.of("payment:create"),
            "jwt-payment"
    );

    private InMemoryPaymentRepository paymentRepository;
    private StubOrderPaymentClient orderPaymentClient;
    private StubProductInventoryClient productInventoryClient;
    private PaymentPayService paymentPayService;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        orderPaymentClient = new StubOrderPaymentClient();
        productInventoryClient = new StubProductInventoryClient();
        paymentPayService = new PaymentPayService(paymentRepository, orderPaymentClient, productInventoryClient);
    }

    @Test
    void payCreatesPaymentAndMarksOrderPaid() {
        orderPaymentClient.seedSnapshot(new OrderPaymentSnapshot(101L, "ORD-001", 1L, "10001", "ord:10001:req-001", "created", 59900L));

        PaymentResponse response = paymentPayService.pay(
                USER_PRINCIPAL,
                new CreatePaymentRequest(999L, "spoof-user", 101L, "PAY-001", "mock"),
                "trace-pay-1"
        );

        assertThat(response.paymentId()).isEqualTo(10001L);
        assertThat(response.paymentNo()).isEqualTo("PAY-001");
        assertThat(response.orderId()).isEqualTo(101L);
        assertThat(response.shopId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo("10001");
        assertThat(response.status()).isEqualTo("paid");
        assertThat(orderPaymentClient.confirmCalls).isEqualTo(1);
        assertThat(productInventoryClient.confirmCalls).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void payReturnsExistingPaidPaymentForSamePaymentNo() {
        orderPaymentClient.seedSnapshot(new OrderPaymentSnapshot(101L, "ORD-001", 1L, "10001", "ord:10001:req-001", "created", 59900L));

        PaymentResponse first = paymentPayService.pay(
                USER_PRINCIPAL,
                new CreatePaymentRequest(1L, "u", 101L, "PAY-001", "mock"),
                "trace-pay-1"
        );
        PaymentResponse second = paymentPayService.pay(
                USER_PRINCIPAL,
                new CreatePaymentRequest(1L, "u", 101L, "PAY-001", "mock"),
                "trace-pay-2"
        );

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(second.status()).isEqualTo("paid");
        assertThat(orderPaymentClient.confirmCalls).isEqualTo(1);
        assertThat(productInventoryClient.confirmCalls).isEqualTo(1);
    }

    @Test
    void payRetriesExistingCreatedPaymentAndMarksItPaid() {
        paymentRepository.seed(new PaymentOrderRecord(
                10001L,
                1L,
                101L,
                "ORD-001",
                "10001",
                "ord:10001:req-001",
                "PAY-001",
                "mock",
                59900L,
                PaymentStatus.CREATED,
                "trace-created",
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null
        ));
        orderPaymentClient.seedSnapshot(new OrderPaymentSnapshot(101L, "ORD-001", 1L, "10001", "ord:10001:req-001", "created", 59900L));

        PaymentResponse response = paymentPayService.pay(
                USER_PRINCIPAL,
                new CreatePaymentRequest(1L, "u", 101L, "PAY-001", "mock"),
                "trace-pay-retry"
        );

        assertThat(response.status()).isEqualTo("paid");
        assertThat(orderPaymentClient.confirmCalls).isEqualTo(1);
        assertThat(productInventoryClient.confirmCalls).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void payRejectsPaymentNoReuseWithDifferentPayload() {
        paymentRepository.seed(new PaymentOrderRecord(
                10001L,
                1L,
                101L,
                "ORD-001",
                "10001",
                "ord:10001:req-001",
                "PAY-001",
                "mock",
                59900L,
                PaymentStatus.PAID,
                "trace-paid",
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatThrownBy(() -> paymentPayService.pay(
                USER_PRINCIPAL,
                new CreatePaymentRequest(1L, "u", 102L, "PAY-001", "mock"),
                "trace-pay-conflict"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {

        private final AtomicLong nextPaymentId = new AtomicLong(10000);
        private final Map<String, PaymentOrderRecord> recordsByKey = new LinkedHashMap<>();

        @Override
        public Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo) {
            return Optional.ofNullable(recordsByKey.get(key(shopId, paymentNo)));
        }

        @Override
        public List<PaymentOrderRecord> findCreatedPayments(Long shopId, LocalDateTime createdBefore, int limit) {
            return recordsByKey.values().stream()
                    .filter(record -> java.util.Objects.equals(record.shopId(), shopId))
                    .filter(record -> record.status() == PaymentStatus.CREATED)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PaymentOrderRecord> findFailedPayments(Long shopId, int limit) {
            return List.of();
        }

        @Override
        public Optional<PaymentCallbackLogRecord> findCallbackLog(String channel, String channelTradeNo) {
            return Optional.empty();
        }

        @Override
        public Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status) {
            Long paymentId = nextPaymentId.incrementAndGet();
            recordsByKey.put(key(draft.shopId(), draft.paymentNo()), new PaymentOrderRecord(
                    paymentId,
                    draft.shopId(),
                    draft.orderId(),
                    draft.orderNo(),
                    draft.userId(),
                    draft.reservationNo(),
                    draft.paymentNo(),
                    draft.channel(),
                    draft.amountCent(),
                    status,
                    draft.traceId(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            return paymentId;
        }

        @Override
        public Long createCallbackLog(PaymentCallbackLogDraft draft) {
            return 1L;
        }

        @Override
        public void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status) {
            recordsByKey.replaceAll((key, value) -> {
                if (java.util.Objects.equals(value.shopId(), shopId) && java.util.Objects.equals(value.id(), paymentId)) {
                    return new PaymentOrderRecord(
                            value.id(),
                            value.shopId(),
                            value.orderId(),
                            value.orderNo(),
                            value.userId(),
                            value.reservationNo(),
                            value.paymentNo(),
                            value.channel(),
                            value.amountCent(),
                            status,
                            value.traceId(),
                            value.createdAt(),
                            LocalDateTime.now(),
                            value.lastCompensationResult(),
                            value.lastCompensationErrorCode(),
                            value.lastCompensationReason(),
                            value.lastCompensationTraceId(),
                            value.lastCompensationTrigger(),
                            value.lastCompensatedAt()
                    );
                }
                return value;
            });
        }

        @Override
        public void updateCompensationMetadata(
                Long shopId,
                Long paymentId,
                String result,
                String errorCode,
                String reason,
                String traceId,
                String trigger,
                LocalDateTime compensatedAt
        ) {
        }

        @Override
        public void updateCallbackProcessStatus(Long callbackLogId, String processStatus) {
        }

        private void seed(PaymentOrderRecord record) {
            recordsByKey.put(key(record.shopId(), record.paymentNo()), record);
        }

        private String key(Long shopId, String paymentNo) {
            return shopId + "|" + paymentNo;
        }
    }

    private static final class StubOrderPaymentClient implements OrderPaymentClient {

        private final Map<Long, OrderPaymentSnapshot> snapshotsByOrderId = new LinkedHashMap<>();
        private int confirmCalls;

        @Override
        public OrderPaymentSnapshot getPayableOrder(Long shopId, String userId, Long orderId) {
            return snapshotsByOrderId.get(orderId);
        }

        @Override
        public OrderPaymentSnapshot confirmPaid(
                Long shopId,
                String userId,
                Long orderId,
                String paymentNo,
                Long paidAmountCent,
                String traceId
        ) {
            confirmCalls++;
            OrderPaymentSnapshot snapshot = snapshotsByOrderId.get(orderId);
            return new OrderPaymentSnapshot(
                    snapshot.orderId(),
                    snapshot.orderNo(),
                    snapshot.shopId(),
                    snapshot.userId(),
                    snapshot.reservationNo(),
                    "paid",
                    snapshot.totalAmountCent()
            );
        }

        private void seedSnapshot(OrderPaymentSnapshot snapshot) {
            snapshotsByOrderId.put(snapshot.orderId(), snapshot);
        }
    }

    private static final class StubProductInventoryClient implements ProductInventoryClient {

        private int confirmCalls;

        @Override
        public void confirmReservation(Long shopId, String reservationNo, String traceId) {
            confirmCalls++;
        }
    }
}
