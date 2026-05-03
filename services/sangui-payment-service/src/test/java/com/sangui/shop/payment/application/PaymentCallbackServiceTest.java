package com.sangui.shop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.api.dto.PaymentCallbackRequest;
import com.sangui.shop.payment.api.dto.PaymentCallbackResponse;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentCallbackServiceTest {

    private InMemoryPaymentRepository paymentRepository;
    private StubOrderPaymentClient orderPaymentClient;
    private StubProductInventoryClient productInventoryClient;
    private PaymentCallbackService paymentCallbackService;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        orderPaymentClient = new StubOrderPaymentClient();
        productInventoryClient = new StubProductInventoryClient();
        PaymentPayService paymentPayService = new PaymentPayService(
                paymentRepository,
                orderPaymentClient,
                productInventoryClient
        );
        paymentCallbackService = new PaymentCallbackService(paymentRepository, paymentPayService);
    }

    @Test
    void duplicateSuccessCallbackSettlesPaymentOnceAndReusesPaidState() {
        paymentRepository.seed(createdPayment());

        PaymentCallbackRequest callback = successCallback("MOCK-TXN-001");
        PaymentCallbackResponse first = paymentCallbackService.handleCallback(callback, "trace-callback-1");
        PaymentCallbackResponse second = paymentCallbackService.handleCallback(callback, "trace-callback-2");

        assertThat(first.paymentStatus()).isEqualTo("paid");
        assertThat(second.paymentStatus()).isEqualTo("paid");
        assertThat(orderPaymentClient.confirmCalls).isEqualTo(1);
        assertThat(productInventoryClient.confirmCalls).isEqualTo(1);
        assertThat(paymentRepository.callbackLogsByKey).hasSize(1);
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void failureCallbackMarksCreatedPaymentFailedWithoutInventoryMutation() {
        paymentRepository.seed(createdPayment());

        PaymentCallbackResponse response = paymentCallbackService.handleCallback(
                new PaymentCallbackRequest(1L, "PAY-001", "mock", "MOCK-TXN-002", "FAILED", 59900L, null, null, null),
                "trace-callback-fail"
        );

        assertThat(response.paymentStatus()).isEqualTo("failed");
        assertThat(orderPaymentClient.confirmCalls).isZero();
        assertThat(productInventoryClient.confirmCalls).isZero();
        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void successCallbackAfterOrderCancelledDoesNotMarkPaymentPaid() {
        paymentRepository.seed(createdPayment());
        orderPaymentClient.rejectConfirm = true;

        assertThatThrownBy(() -> paymentCallbackService.handleCallback(successCallback("MOCK-TXN-003"), "trace-late-success"))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo(PaymentErrorCode.PAYMENT_ORDER_STATUS_INVALID.code());
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });

        assertThat(paymentRepository.findByPaymentNo(1L, "PAY-001").orElseThrow().status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(productInventoryClient.confirmCalls).isZero();
        assertThat(paymentRepository.findCallbackLog("mock", "MOCK-TXN-003").orElseThrow().processStatus()).isEqualTo("failed");
    }

    private PaymentOrderRecord createdPayment() {
        LocalDateTime now = LocalDateTime.now();
        return new PaymentOrderRecord(
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
                now,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private PaymentCallbackRequest successCallback(String channelTradeNo) {
        return new PaymentCallbackRequest(
                1L,
                "PAY-001",
                "mock",
                channelTradeNo,
                "SUCCESS",
                59900L,
                "payment",
                "2026-05-01T21:30:00+08:00",
                "{\"ok\":true}"
        );
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {

        private final AtomicLong nextCallbackId = new AtomicLong(20000);
        private final Map<String, PaymentOrderRecord> recordsByKey = new LinkedHashMap<>();
        private final Map<String, PaymentCallbackLogRecord> callbackLogsByKey = new LinkedHashMap<>();

        @Override
        public Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo) {
            return Optional.ofNullable(recordsByKey.get(paymentKey(shopId, paymentNo)));
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
            return Optional.ofNullable(callbackLogsByKey.get(callbackKey(channel, channelTradeNo)));
        }

        @Override
        public Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status) {
            recordsByKey.put(paymentKey(draft.shopId(), draft.paymentNo()), new PaymentOrderRecord(
                    10001L,
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
                    null,
                    null
            ));
            return 10001L;
        }

        @Override
        public Long createCallbackLog(PaymentCallbackLogDraft draft) {
            String key = callbackKey(draft.channel(), draft.channelTradeNo());
            PaymentCallbackLogRecord existing = callbackLogsByKey.get(key);
            if (existing != null) {
                return existing.id();
            }
            Long callbackId = nextCallbackId.incrementAndGet();
            callbackLogsByKey.put(key, new PaymentCallbackLogRecord(
                    callbackId,
                    draft.shopId(),
                    draft.paymentNo(),
                    draft.channel(),
                    draft.channelTradeNo(),
                    draft.callbackType(),
                    "received",
                    draft.traceId()
            ));
            return callbackId;
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
        }

        @Override
        public void updateCallbackProcessStatus(Long callbackLogId, String processStatus) {
            callbackLogsByKey.replaceAll((key, value) -> {
                if (java.util.Objects.equals(value.id(), callbackLogId)) {
                    return new PaymentCallbackLogRecord(
                            value.id(),
                            value.shopId(),
                            value.paymentNo(),
                            value.channel(),
                            value.channelTradeNo(),
                            value.callbackType(),
                            processStatus,
                            value.traceId()
                    );
                }
                return value;
            });
        }

        private void seed(PaymentOrderRecord record) {
            recordsByKey.put(paymentKey(record.shopId(), record.paymentNo()), record);
        }

        private String paymentKey(Long shopId, String paymentNo) {
            return shopId + "|" + paymentNo;
        }

        private String callbackKey(String channel, String channelTradeNo) {
            return channel + "|" + channelTradeNo;
        }
    }

    private static final class StubOrderPaymentClient implements OrderPaymentClient {

        private int confirmCalls;
        private boolean rejectConfirm;

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
