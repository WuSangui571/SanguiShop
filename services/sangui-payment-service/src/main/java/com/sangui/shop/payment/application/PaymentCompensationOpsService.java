package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileItemResponse;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationAggregateResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationAttemptResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryRequest;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationRecordResponse;
import com.sangui.shop.payment.domain.PaymentCompensationAttemptQuery;
import com.sangui.shop.payment.domain.PaymentCompensationAttemptRecord;
import com.sangui.shop.payment.domain.PaymentCompensationAttemptSummary;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentCompensationOpsService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompensationOpsService.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_MIN_AGE_MINUTES = 1;
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository paymentRepository;
    private final PaymentReconcileService paymentReconcileService;
    private final PaymentCompensationMetricsRecorder metricsRecorder;
    private final Clock clock;

    @Autowired
    public PaymentCompensationOpsService(
            PaymentRepository paymentRepository,
            PaymentReconcileService paymentReconcileService,
            PaymentCompensationMetricsRecorder metricsRecorder
    ) {
        this(paymentRepository, paymentReconcileService, metricsRecorder, Clock.systemDefaultZone());
    }

    PaymentCompensationOpsService(
            PaymentRepository paymentRepository,
            PaymentReconcileService paymentReconcileService,
            PaymentCompensationMetricsRecorder metricsRecorder,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentReconcileService = paymentReconcileService;
        this.metricsRecorder = metricsRecorder;
        this.clock = clock;
    }

    public PaymentCompensationQueryResponse queryRecords(PaymentCompensationQueryRequest request) {
        PaymentCompensationAttemptQuery query = toAttemptQuery(request);
        int pageNo = normalizePageNo(request.pageNo());
        int pageSize = normalizePageSize(request.pageSize());
        int offset = (pageNo - 1) * pageSize;
        long total = paymentRepository.countCompensationAttempts(query);
        if (total == 0) {
            return new PaymentCompensationQueryResponse(request.shopId(), pageNo, pageSize, 0L, List.of());
        }
        List<PaymentCompensationAttemptSummary> summaries = paymentRepository.findCompensationAttemptSummaries(query, offset, pageSize);
        List<Long> paymentIds = summaries.stream()
                .map(PaymentCompensationAttemptSummary::paymentId)
                .toList();
        Map<Long, PaymentCompensationAttemptSummary> summaryByPaymentId = new LinkedHashMap<>();
        for (PaymentCompensationAttemptSummary summary : summaries) {
            summaryByPaymentId.put(summary.paymentId(), summary);
        }
        Map<Long, List<PaymentCompensationAttemptResponse>> attemptsByPaymentId = groupAttemptsByPaymentId(
                paymentRepository.findCompensationAttemptsByPaymentIds(request.shopId(), paymentIds)
        );
        List<PaymentCompensationAggregateResponse> items = new ArrayList<>();
        for (PaymentCompensationAttemptSummary summary : summaries) {
            paymentRepository.findByPaymentNo(request.shopId(), summary.paymentNo()).ifPresent(payment -> {
                List<PaymentCompensationAttemptResponse> attempts = attemptsByPaymentId.getOrDefault(summary.paymentId(), List.of());
                items.add(new PaymentCompensationAggregateResponse(
                        toResponse(payment),
                        summary.matchedAttemptCount(),
                        (long) attempts.size(),
                        toOffsetDateTime(summary.latestAttemptAt()),
                        attempts
                ));
            });
        }
        return new PaymentCompensationQueryResponse(request.shopId(), pageNo, pageSize, total, items);
    }

    public ManualPaymentReconcileResponse manualReconcile(ManualPaymentReconcileRequest request, String traceId) {
        long startedAt = System.nanoTime();
        log.info(
                "Starting manual payment reconcile. traceId={} shopId={} paymentNo={} operator={}",
                traceId,
                request.shopId(),
                request.paymentNo(),
                request.operator()
        );
        try {
            PaymentCompensationExecution execution = paymentReconcileService.reconcilePayment(
                    request.shopId(),
                    request.paymentNo(),
                    traceId,
                    "manual",
                    request.operator()
            );
            metricsRecorder.incrementRun("manual", "success");
            metricsRecorder.incrementItem("manual", execution.result(), 1);
            log.info(
                    "Completed manual payment reconcile. traceId={} shopId={} paymentNo={} result={} durationMs={} operator={}",
                    traceId,
                    request.shopId(),
                    request.paymentNo(),
                    execution.result(),
                    elapsedMillis(startedAt),
                    request.operator()
            );
            return new ManualPaymentReconcileResponse(
                    execution.result(),
                    execution.errorCode(),
                    execution.reason(),
                    toResponse(execution.payment())
            );
        } catch (RuntimeException exception) {
            metricsRecorder.incrementRun("manual", "failed");
            throw exception;
        }
    }

    public BulkPaymentReconcileResponse bulkReconcile(BulkPaymentReconcileRequest request, String traceId) {
        validateBulkRequest(request);
        int limit = normalizeLimit(request.limit());
        int minAgeMinutes = request.minAgeMinutes() == null ? DEFAULT_MIN_AGE_MINUTES : request.minAgeMinutes();
        List<PaymentOrderRecord> candidates = resolveBulkCandidates(request.shopId(), minAgeMinutes, limit, request.paymentNos());
        long startedAt = System.nanoTime();
        log.info(
                "Starting bulk payment reconcile. traceId={} shopId={} dryRun={} minAgeMinutes={} limit={} operator={} explicitPaymentNos={}",
                traceId,
                request.shopId(),
                request.dryRun(),
                minAgeMinutes,
                limit,
                request.operator(),
                request.paymentNos() == null ? 0 : request.paymentNos().size()
        );
        List<BulkPaymentReconcileItemResponse> items = new ArrayList<>();
        int executedCount = 0;
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        try {
            for (PaymentOrderRecord candidate : candidates) {
                if (Boolean.TRUE.equals(request.dryRun())) {
                    BulkPaymentReconcileItemResponse preview = preview(candidate);
                    items.add(preview);
                    if (Objects.equals(preview.result(), "skipped")) {
                        skippedCount++;
                    }
                    continue;
                }
                PaymentCompensationExecution execution = paymentReconcileService.reconcilePayment(
                        candidate.shopId(),
                        candidate.paymentNo(),
                        traceId,
                        "manual",
                        request.operator()
                );
                executedCount++;
                items.add(new BulkPaymentReconcileItemResponse(
                        execution.result(),
                        execution.errorCode(),
                        execution.reason(),
                        toResponse(execution.payment())
                ));
                if (Objects.equals(execution.result(), "settled")) {
                    successCount++;
                } else if (Objects.equals(execution.result(), "skipped")) {
                    skippedCount++;
                } else {
                    failedCount++;
                }
            }
            if (!Boolean.TRUE.equals(request.dryRun())) {
                metricsRecorder.incrementRun("manual", "success");
                metricsRecorder.incrementItem("manual", "settled", successCount);
                metricsRecorder.incrementItem("manual", "skipped", skippedCount);
                metricsRecorder.incrementItem("manual", "failed", failedCount);
            }
            log.info(
                    "Completed bulk payment reconcile. traceId={} shopId={} dryRun={} matchedCount={} executedCount={} successCount={} skippedCount={} failedCount={} durationMs={} operator={}",
                    traceId,
                    request.shopId(),
                    request.dryRun(),
                    items.size(),
                    executedCount,
                    successCount,
                    skippedCount,
                    failedCount,
                    elapsedMillis(startedAt),
                    request.operator()
            );
            return new BulkPaymentReconcileResponse(
                    request.shopId(),
                    request.dryRun(),
                    items.size(),
                    executedCount,
                    successCount,
                    skippedCount,
                    failedCount,
                    items
            );
        } catch (RuntimeException exception) {
            if (!Boolean.TRUE.equals(request.dryRun())) {
                metricsRecorder.incrementRun("manual", "failed");
            }
            throw exception;
        }
    }

    private PaymentCompensationRecordResponse toResponse(PaymentOrderRecord payment) {
        return new PaymentCompensationRecordResponse(
                payment.id(),
                payment.paymentNo(),
                payment.orderId(),
                payment.orderNo(),
                payment.userId(),
                payment.channel(),
                payment.status().value(),
                payment.amountCent(),
                payment.traceId(),
                toOffsetDateTime(payment.createdAt()),
                toOffsetDateTime(payment.updatedAt()),
                payment.lastCompensationResult(),
                payment.lastCompensationErrorCode(),
                payment.lastCompensationReason(),
                payment.lastCompensationTraceId(),
                payment.lastCompensationTrigger(),
                payment.lastCompensationOperator(),
                toOffsetDateTime(payment.lastCompensatedAt())
        );
    }

    private PaymentCompensationAttemptResponse toAttemptResponse(PaymentCompensationAttemptRecord attempt) {
        return new PaymentCompensationAttemptResponse(
                attempt.id(),
                attempt.paymentId(),
                attempt.orderId(),
                attempt.paymentNo(),
                attempt.orderNo(),
                attempt.reservationNo(),
                attempt.result(),
                attempt.errorCode(),
                attempt.reason(),
                attempt.traceId(),
                attempt.trigger(),
                attempt.operator(),
                toOffsetDateTime(attempt.createdAt()),
                toOffsetDateTime(attempt.updatedAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private PaymentCompensationAttemptQuery toAttemptQuery(PaymentCompensationQueryRequest request) {
        OffsetDateTime fromTime = request.fromTime();
        OffsetDateTime toTime = request.toTime();
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return new PaymentCompensationAttemptQuery(
                request.shopId(),
                request.orderId(),
                normalizeOptionalFilter(request.paymentNo()),
                normalizeOptionalFilter(request.trigger()),
                normalizeOptionalFilter(request.result()),
                normalizeOptionalFilter(request.operator()),
                normalizeOptionalFilter(request.traceId()),
                toLocalDateTime(fromTime),
                toLocalDateTime(toTime)
        );
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String normalizeOptionalFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return trimmed;
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null ? DEFAULT_PAGE_NO : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private Map<Long, List<PaymentCompensationAttemptResponse>> groupAttemptsByPaymentId(List<PaymentCompensationAttemptRecord> attempts) {
        Map<Long, List<PaymentCompensationAttemptResponse>> attemptsByPaymentId = new LinkedHashMap<>();
        for (PaymentCompensationAttemptRecord attempt : attempts) {
            attemptsByPaymentId.computeIfAbsent(attempt.paymentId(), ignored -> new ArrayList<>())
                    .add(toAttemptResponse(attempt));
        }
        return attemptsByPaymentId;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void validateBulkRequest(BulkPaymentReconcileRequest request) {
        boolean hasExplicitKeys = request.paymentNos() != null && !request.paymentNos().isEmpty();
        if (!hasExplicitKeys && request.minAgeMinutes() == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        if (hasExplicitKeys && request.paymentNos().size() > MAX_LIMIT) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private List<PaymentOrderRecord> resolveBulkCandidates(Long shopId, int minAgeMinutes, int limit, List<String> paymentNos) {
        if (paymentNos == null || paymentNos.isEmpty()) {
            LocalDateTime createdBefore = LocalDateTime.now(clock).minusMinutes(minAgeMinutes);
            return paymentRepository.findCreatedPayments(shopId, createdBefore, limit);
        }
        return paymentNos.stream()
                .map(this::normalizePaymentNo)
                .distinct()
                .limit(limit)
                .map(paymentNo -> paymentRepository.findByPaymentNo(shopId, paymentNo)
                        .orElseThrow(() -> new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400)))
                .toList();
    }

    private BulkPaymentReconcileItemResponse preview(PaymentOrderRecord payment) {
        if (payment.status() != PaymentStatus.CREATED) {
            return new BulkPaymentReconcileItemResponse(
                    "skipped",
                    "PAYMENT_STATUS_NOT_CREATED",
                    "Payment is no longer in created status.",
                    toResponse(payment)
            );
        }
        return new BulkPaymentReconcileItemResponse("would-settle", null, null, toResponse(payment));
    }

    private String normalizePaymentNo(String value) {
        if (value == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return trimmed;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
