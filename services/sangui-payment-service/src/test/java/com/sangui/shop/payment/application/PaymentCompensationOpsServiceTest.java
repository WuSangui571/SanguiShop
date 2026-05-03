package com.sangui.shop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryRequest;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.domain.PaymentCallbackLogDraft;
import com.sangui.shop.payment.domain.PaymentCallbackLogRecord;
import com.sangui.shop.payment.domain.PaymentCompensationAttemptQuery;
import com.sangui.shop.payment.domain.PaymentCompensationAttemptRecord;
import com.sangui.shop.payment.domain.PaymentCompensationAttemptSummary;
import com.sangui.shop.payment.domain.PaymentCreateDraft;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentCompensationOpsServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-03T06:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    private InMemoryPaymentRepository paymentRepository;
    private PaymentCompensationOpsService paymentCompensationOpsService;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        paymentCompensationOpsService = new PaymentCompensationOpsService(
                paymentRepository,
                null,
                new PaymentCompensationMetricsRecorder(new SimpleMeterRegistry()),
                FIXED_CLOCK
        );
    }

    @Test
    void queryRecordsReturnsLatestSnapshotAndAllAttemptsForMatchedPayment() {
        paymentRepository.seedPayment(
                201L,
                "PAY-001",
                PaymentStatus.FAILED,
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "order confirm timeout",
                "trace-latest",
                "scheduler",
                null,
                LocalDateTime.of(2026, 5, 3, 12, 7)
        );
        paymentRepository.seedAttempt(
                1L,
                201L,
                101L,
                "PAY-001",
                "ORD-001",
                "ord:10001:req-001",
                "skipped",
                "PAYMENT_STATUS_NOT_CREATED",
                "already paid",
                "trace-old",
                "manual",
                "ops-a",
                LocalDateTime.of(2026, 5, 3, 12, 3)
        );
        paymentRepository.seedAttempt(
                2L,
                201L,
                101L,
                "PAY-001",
                "ORD-001",
                "ord:10001:req-001",
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "order confirm timeout",
                "trace-latest",
                "scheduler",
                null,
                LocalDateTime.of(2026, 5, 3, 12, 7)
        );

        PaymentCompensationQueryResponse response = paymentCompensationOpsService.queryRecords(new PaymentCompensationQueryRequest(
                1L,
                null,
                "PAY-001",
                null,
                "failed",
                null,
                null,
                null,
                null,
                1,
                10
        ));

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).payment().paymentNo()).isEqualTo("PAY-001");
        assertThat(response.items().get(0).matchedAttemptCount()).isEqualTo(1L);
        assertThat(response.items().get(0).totalAttemptCount()).isEqualTo(2L);
        assertThat(response.items().get(0).attempts()).hasSize(2);
        assertThat(response.items().get(0).attempts().get(0).result()).isEqualTo("failed");
        assertThat(response.items().get(0).attempts().get(1).result()).isEqualTo("skipped");
    }

    @Test
    void queryRecordsPagesDistinctPaymentsByLatestMatchedAttemptTime() {
        paymentRepository.seedPayment(201L, "PAY-001", PaymentStatus.FAILED, "failed", null, null, "trace-201", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 9));
        paymentRepository.seedPayment(202L, "PAY-002", PaymentStatus.FAILED, "failed", null, null, "trace-202", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 12));
        paymentRepository.seedAttempt(1L, 201L, 101L, "PAY-001", "ORD-001", "ord:10001:req-001", "failed", null, null, "trace-201", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 9));
        paymentRepository.seedAttempt(2L, 202L, 102L, "PAY-002", "ORD-002", "ord:10001:req-002", "failed", null, null, "trace-202", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 12));

        PaymentCompensationQueryResponse response = paymentCompensationOpsService.queryRecords(new PaymentCompensationQueryRequest(
                1L,
                null,
                null,
                null,
                "failed",
                null,
                null,
                null,
                null,
                2,
                1
        ));

        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.pageNo()).isEqualTo(2);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).payment().paymentNo()).isEqualTo("PAY-001");
    }

    @Test
    void queryRecordsRejectsBlankPaymentNo() {
        assertThatThrownBy(() -> paymentCompensationOpsService.queryRecords(new PaymentCompensationQueryRequest(
                1L,
                null,
                "   ",
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                10
        )))
                .isInstanceOf(SanguiException.class)
                .satisfies(exception -> {
                    SanguiException sanguiException = (SanguiException) exception;
                    assertThat(sanguiException.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(sanguiException.httpStatus()).isEqualTo(400);
                });
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {

        private final Map<String, PaymentOrderRecord> paymentsByKey = new LinkedHashMap<>();
        private final List<PaymentCompensationAttemptRecord> attempts = new ArrayList<>();

        @Override
        public Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo) {
            return Optional.ofNullable(paymentsByKey.get(shopId + "|" + paymentNo));
        }

        @Override
        public List<PaymentOrderRecord> findCreatedPayments(Long shopId, LocalDateTime createdBefore, int limit) {
            return List.of();
        }

        @Override
        public List<PaymentOrderRecord> findFailedPayments(Long shopId, int limit) {
            return List.of();
        }

        @Override
        public long countCompensationAttempts(PaymentCompensationAttemptQuery query) {
            return filteredAttempts(query).stream()
                    .map(PaymentCompensationAttemptRecord::paymentId)
                    .distinct()
                    .count();
        }

        @Override
        public List<PaymentCompensationAttemptSummary> findCompensationAttemptSummaries(
                PaymentCompensationAttemptQuery query,
                int offset,
                int limit
        ) {
            return filteredAttempts(query).stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            PaymentCompensationAttemptRecord::paymentId,
                            LinkedHashMap::new,
                            java.util.stream.Collectors.toList()
                    ))
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        PaymentCompensationAttemptRecord latest = entry.getValue().stream()
                                .max(Comparator.comparing(PaymentCompensationAttemptRecord::createdAt)
                                        .thenComparing(PaymentCompensationAttemptRecord::id))
                                .orElseThrow();
                        return new PaymentCompensationAttemptSummary(
                                entry.getKey(),
                                latest.paymentNo(),
                                latest.createdAt(),
                                entry.getValue().size()
                        );
                    })
                    .sorted(Comparator.comparing(PaymentCompensationAttemptSummary::latestAttemptAt)
                            .reversed()
                            .thenComparing(PaymentCompensationAttemptSummary::paymentId, Comparator.reverseOrder()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PaymentCompensationAttemptRecord> findCompensationAttemptsByPaymentIds(Long shopId, List<Long> paymentIds) {
            return attempts.stream()
                    .filter(attempt -> attempt.shopId().equals(shopId))
                    .filter(attempt -> paymentIds.contains(attempt.paymentId()))
                    .sorted(Comparator.comparing(PaymentCompensationAttemptRecord::paymentId)
                            .thenComparing(PaymentCompensationAttemptRecord::createdAt, Comparator.reverseOrder())
                            .thenComparing(PaymentCompensationAttemptRecord::id, Comparator.reverseOrder()))
                    .toList();
        }

        @Override
        public Optional<PaymentCallbackLogRecord> findCallbackLog(String channel, String channelTradeNo) {
            return Optional.empty();
        }

        @Override
        public Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long createCallbackLog(PaymentCallbackLogDraft draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status) {
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
        }

        private void seedPayment(
                Long paymentId,
                String paymentNo,
                PaymentStatus status,
                String lastCompensationResult,
                String lastCompensationErrorCode,
                String lastCompensationReason,
                String lastCompensationTraceId,
                String lastCompensationTrigger,
                String lastCompensationOperator,
                LocalDateTime lastCompensatedAt
        ) {
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 3, 12, 0);
            paymentsByKey.put("1|" + paymentNo, new PaymentOrderRecord(
                    paymentId,
                    1L,
                    101L,
                    "ORD-" + paymentId,
                    "10001",
                    "ord:10001:req-" + paymentId,
                    paymentNo,
                    "mock",
                    59900L,
                    status,
                    "trace-payment-" + paymentId,
                    createdAt,
                    createdAt,
                    lastCompensationResult,
                    lastCompensationErrorCode,
                    lastCompensationReason,
                    lastCompensationTraceId,
                    lastCompensationTrigger,
                    lastCompensationOperator,
                    lastCompensatedAt
            ));
        }

        private void seedAttempt(
                Long attemptId,
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
                String operator,
                LocalDateTime createdAt
        ) {
            attempts.add(new PaymentCompensationAttemptRecord(
                    attemptId,
                    1L,
                    paymentId,
                    orderId,
                    paymentNo,
                    orderNo,
                    reservationNo,
                    result,
                    errorCode,
                    reason,
                    traceId,
                    trigger,
                    operator,
                    createdAt,
                    createdAt
            ));
        }

        private List<PaymentCompensationAttemptRecord> filteredAttempts(PaymentCompensationAttemptQuery query) {
            return attempts.stream()
                    .filter(attempt -> attempt.shopId().equals(query.shopId()))
                    .filter(attempt -> query.orderId() == null || attempt.orderId().equals(query.orderId()))
                    .filter(attempt -> query.paymentNo() == null || attempt.paymentNo().equals(query.paymentNo()))
                    .filter(attempt -> query.trigger() == null || attempt.trigger().equals(query.trigger()))
                    .filter(attempt -> query.result() == null || attempt.result().equals(query.result()))
                    .filter(attempt -> query.operator() == null || java.util.Objects.equals(attempt.operator(), query.operator()))
                    .filter(attempt -> query.traceId() == null || java.util.Objects.equals(attempt.traceId(), query.traceId()))
                    .filter(attempt -> query.fromTime() == null || !attempt.createdAt().isBefore(query.fromTime()))
                    .filter(attempt -> query.toTime() == null || !attempt.createdAt().isAfter(query.toTime()))
                    .toList();
        }
    }
}
