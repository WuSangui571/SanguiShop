package com.sangui.shop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.client.OrderPaymentClient;
import com.sangui.shop.payment.client.OrderPaymentSnapshot;
import com.sangui.shop.payment.client.ProductInventoryClient;
import com.sangui.shop.payment.domain.PaymentCallbackLogDraft;
import com.sangui.shop.payment.domain.PaymentCallbackLogRecord;
import com.sangui.shop.payment.domain.PaymentCreateDraft;
import com.sangui.shop.payment.domain.PaymentErrorCode;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
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

class PaymentReconcileServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-03T06:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    private InMemoryPaymentRepository paymentRepository;
    private StubOrderPaymentClient orderPaymentClient;
    private StubProductInventoryClient productInventoryClient;
    private PaymentReconcileService paymentReconcileService;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        orderPaymentClient = new StubOrderPaymentClient();
        productInventoryClient = new StubProductInventoryClient();
        PaymentPayService paymentPayService = new PaymentPayService(paymentRepository, orderPaymentClient, productInventoryClient);
        paymentReconcileService = new PaymentReconcileService(paymentRepository, paymentPayService, FIXED_CLOCK);
    }

    @Test
    void reconcileCreatedPaymentsSettlesEligibleRows() {
        paymentRepository.seed(createdPayment("PAY-001"), LocalDateTime.now(FIXED_CLOCK).minusMinutes(5));

        PaymentReconcileResult result = paymentReconcileService.reconcileCreatedPayments(1L, 1, 100, "trace-reconcile-1");

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(orderPaymentClient.confirmCalls).isEqualTo(1);
        assertThat(productInventoryClient.confirmCalls).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void reconcileCreatedPaymentsMarksTerminalInvalidOrderAsFailed() {
        paymentRepository.seed(createdPayment("PAY-001"), LocalDateTime.now(FIXED_CLOCK).minusMinutes(5));
        orderPaymentClient.rejectConfirm = true;

        PaymentReconcileResult result = paymentReconcileService.reconcileCreatedPayments(1L, 1, 100, "trace-reconcile-invalid");

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.settledCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void reconcileCreatedPaymentsContinuesAfterSingleFailure() {
        paymentRepository.seed(createdPayment("PAY-001"), LocalDateTime.now(FIXED_CLOCK).minusMinutes(5));
        paymentRepository.seed(createdPayment("PAY-002"), LocalDateTime.now(FIXED_CLOCK).minusMinutes(6));
        orderPaymentClient.failPaymentNo = "PAY-001";

        PaymentReconcileResult result = paymentReconcileService.reconcileCreatedPayments(1L, 1, 100, "trace-reconcile-partial");

        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-002").orElseThrow().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void manualReconcileRecordsLatestCompensationMetadata() {
        paymentRepository.seed(createdPayment("PAY-003"), LocalDateTime.now(FIXED_CLOCK).minusMinutes(5));

        PaymentCompensationExecution execution = paymentReconcileService.reconcilePayment(1L, "PAY-003", "trace-manual", "manual", "ops-user");

        assertThat(execution.result()).isEqualTo("settled");
        PaymentOrderRecord payment = paymentRepository.findByPaymentNo(1L, "PAY-003").orElseThrow();
        assertThat(payment.lastCompensationResult()).isEqualTo("settled");
        assertThat(payment.lastCompensationTraceId()).isEqualTo("trace-manual");
        assertThat(payment.lastCompensationTrigger()).isEqualTo("manual");
        assertThat(payment.lastCompensationOperator()).isEqualTo("ops-user");
        assertThat(payment.lastCompensatedAt()).isNotNull();
        assertThat(paymentRepository.compensationAttempts).hasSize(1);
    }

    private PaymentOrderRecord createdPayment(String paymentNo) {
        LocalDateTime createdAt = LocalDateTime.now(FIXED_CLOCK).minusMinutes(5);
        return new PaymentOrderRecord(
                paymentRepository.nextPaymentId.incrementAndGet(),
                1L,
                101L,
                "ORD-" + paymentNo,
                "10001",
                "ord:10001:req-" + paymentNo,
                paymentNo,
                "mock",
                59900L,
                PaymentStatus.CREATED,
                "trace-created-" + paymentNo,
                createdAt,
                createdAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private final class InMemoryPaymentRepository implements PaymentRepository {

        private final AtomicLong nextPaymentId = new AtomicLong(10000);
        private final Map<String, PaymentOrderRecord> recordsByKey = new LinkedHashMap<>();
        private final Map<String, LocalDateTime> createdAtByKey = new LinkedHashMap<>();
        private final List<String> compensationAttempts = new java.util.ArrayList<>();

        @Override
        public Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo) {
            return Optional.ofNullable(recordsByKey.get(key(shopId, paymentNo)));
        }

        @Override
        public List<PaymentOrderRecord> findCreatedPayments(Long shopId, LocalDateTime createdBefore, int limit) {
            return recordsByKey.values().stream()
                    .filter(record -> java.util.Objects.equals(record.shopId(), shopId))
                    .filter(record -> record.status() == PaymentStatus.CREATED)
                    .filter(record -> !createdAtByKey.get(key(record.shopId(), record.paymentNo())).isAfter(createdBefore))
                    .sorted(Comparator.comparing(PaymentOrderRecord::id))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PaymentOrderRecord> findFailedPayments(Long shopId, int limit) {
            return recordsByKey.values().stream()
                    .filter(record -> java.util.Objects.equals(record.shopId(), shopId))
                    .filter(record -> record.status() == PaymentStatus.FAILED)
                    .sorted(Comparator.comparing(PaymentOrderRecord::updatedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<PaymentCallbackLogRecord> findCallbackLog(String channel, String channelTradeNo) {
            return Optional.empty();
        }

        @Override
        public Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status) {
            Long paymentId = nextPaymentId.incrementAndGet();
            seed(new PaymentOrderRecord(
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
                    LocalDateTime.now(FIXED_CLOCK),
                    LocalDateTime.now(FIXED_CLOCK),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ), LocalDateTime.now(FIXED_CLOCK));
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
                            LocalDateTime.now(FIXED_CLOCK),
                            value.lastCompensationResult(),
                            value.lastCompensationErrorCode(),
                            value.lastCompensationReason(),
                            value.lastCompensationTraceId(),
                            value.lastCompensationTrigger(),
                            value.lastCompensationOperator(),
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
                String operator,
                LocalDateTime compensatedAt
        ) {
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
                            value.status(),
                            value.traceId(),
                            value.createdAt(),
                            compensatedAt,
                            result,
                            errorCode,
                            reason,
                            traceId,
                            trigger,
                            operator,
                            compensatedAt
                    );
                }
                return value;
            });
        }

        @Override
        public void appendCompensationAttempt(
                Long shopId,
                Long paymentId,
                Long orderId,
                String paymentNo,
                String orderNo,
                String reservationNo,
                String result,
                String errorCode,
                String reason,
                String traceId,
                String trigger,
                String operator
        ) {
            compensationAttempts.add(trigger + "|" + result + "|" + operator);
        }

        @Override
        public void updateCallbackProcessStatus(Long callbackLogId, String processStatus) {
        }

        private void seed(PaymentOrderRecord record, LocalDateTime createdAt) {
            recordsByKey.put(key(record.shopId(), record.paymentNo()), record);
            createdAtByKey.put(key(record.shopId(), record.paymentNo()), createdAt);
        }

        private String key(Long shopId, String paymentNo) {
            return shopId + "|" + paymentNo;
        }
    }

    private static final class StubOrderPaymentClient implements OrderPaymentClient {

        private int confirmCalls;
        private boolean rejectConfirm;
        private String failPaymentNo;

        @Override
        public OrderPaymentSnapshot getPayableOrder(Long shopId, String userId, Long orderId) {
            return new OrderPaymentSnapshot(orderId, "ORD-001", shopId, userId, "ord:10001:req-001", "created", 59900L);
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
            if (rejectConfirm) {
                throw new SanguiException(PaymentErrorCode.PAYMENT_ORDER_STATUS_INVALID, 409);
            }
            if (java.util.Objects.equals(failPaymentNo, paymentNo)) {
                throw new IllegalStateException("simulated downstream timeout");
            }
            return new OrderPaymentSnapshot(orderId, "ORD-001", shopId, userId, "ord:10001:req-001", "paid", paidAmountCent);
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
