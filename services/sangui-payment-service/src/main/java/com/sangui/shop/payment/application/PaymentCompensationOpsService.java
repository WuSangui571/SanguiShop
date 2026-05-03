package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileItemResponse;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.BulkPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryRequest;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationRecordResponse;
import com.sangui.shop.payment.domain.PaymentErrorCode;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
        int limit = normalizeLimit(request.limit());
        int minAgeMinutes = request.minAgeMinutes() == null ? DEFAULT_MIN_AGE_MINUTES : request.minAgeMinutes();
        LocalDateTime createdBefore = LocalDateTime.now(clock).minusMinutes(minAgeMinutes);
        List<PaymentCompensationRecordResponse> createdPayments = paymentRepository.findCreatedPayments(
                        request.shopId(),
                        createdBefore,
                        limit
                ).stream()
                .map(this::toResponse)
                .toList();
        List<PaymentCompensationRecordResponse> failedPayments = paymentRepository.findFailedPayments(request.shopId(), limit)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PaymentCompensationQueryResponse(request.shopId(), createdPayments, failedPayments);
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

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
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
