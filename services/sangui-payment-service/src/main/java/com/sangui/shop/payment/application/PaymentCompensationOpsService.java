package com.sangui.shop.payment.application;

import com.sangui.shop.payment.api.dto.ManualPaymentReconcileRequest;
import com.sangui.shop.payment.api.dto.ManualPaymentReconcileResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryRequest;
import com.sangui.shop.payment.api.dto.PaymentCompensationQueryResponse;
import com.sangui.shop.payment.api.dto.PaymentCompensationRecordResponse;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
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
                "Starting manual payment reconcile. traceId={} shopId={} paymentNo={}",
                traceId,
                request.shopId(),
                request.paymentNo()
        );
        try {
            PaymentCompensationExecution execution = paymentReconcileService.reconcilePayment(
                    request.shopId(),
                    request.paymentNo(),
                    traceId,
                    "manual"
            );
            metricsRecorder.incrementRun("success");
            metricsRecorder.incrementItem(execution.result(), 1);
            log.info(
                    "Completed manual payment reconcile. traceId={} shopId={} paymentNo={} result={} durationMs={}",
                    traceId,
                    request.shopId(),
                    request.paymentNo(),
                    execution.result(),
                    elapsedMillis(startedAt)
            );
            return new ManualPaymentReconcileResponse(
                    execution.result(),
                    execution.errorCode(),
                    execution.reason(),
                    toResponse(execution.payment())
            );
        } catch (RuntimeException exception) {
            metricsRecorder.incrementRun("failed");
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

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
