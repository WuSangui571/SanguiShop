package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.domain.PaymentErrorCode;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentReconcileService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_MIN_AGE_MINUTES = 1;
    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentPayService paymentPayService;
    private final Clock clock;

    @Autowired
    public PaymentReconcileService(PaymentRepository paymentRepository, PaymentPayService paymentPayService) {
        this(paymentRepository, paymentPayService, Clock.systemDefaultZone());
    }

    PaymentReconcileService(PaymentRepository paymentRepository, PaymentPayService paymentPayService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentPayService = paymentPayService;
        this.clock = clock;
    }

    public PaymentReconcileResult reconcileCreatedPayments(Long shopId, Integer minAgeMinutes, Integer limit, String traceId) {
        int normalizedLimit = normalizeLimit(limit);
        int normalizedMinAgeMinutes = minAgeMinutes == null ? DEFAULT_MIN_AGE_MINUTES : minAgeMinutes;
        LocalDateTime createdBefore = LocalDateTime.now(clock).minusMinutes(normalizedMinAgeMinutes);
        List<PaymentOrderRecord> payments = paymentRepository.findCreatedPayments(shopId, createdBefore, normalizedLimit);

        int settledCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        for (PaymentOrderRecord payment : payments) {
            PaymentCompensationExecution execution = reconcilePayment(payment.shopId(), payment.paymentNo(), traceId, "scheduler", null);
            if ("settled".equals(execution.result())) {
                settledCount++;
            } else if ("skipped".equals(execution.result())) {
                skippedCount++;
            } else {
                failedCount++;
            }
        }

        return new PaymentReconcileResult(shopId, payments.size(), settledCount, skippedCount, failedCount);
    }

    public PaymentCompensationExecution reconcilePayment(
            Long shopId,
            String paymentNo,
            String traceId,
            String trigger,
            String operator
    ) {
        PaymentOrderRecord latest = paymentRepository.findByPaymentNo(shopId, paymentNo)
                .orElseThrow(() -> new SanguiException(PaymentErrorCode.PAYMENT_NOT_FOUND, 404));
        if (latest.status() != PaymentStatus.CREATED) {
            return recordSkipped(latest, traceId, trigger, operator, "PAYMENT_STATUS_NOT_CREATED", "Payment is no longer in created status.");
        }
        try {
            paymentPayService.settlePayment(latest, traceId);
            PaymentOrderRecord refreshed = paymentRepository.findByPaymentNo(latest.shopId(), latest.paymentNo())
                    .orElse(latest.withStatus(PaymentStatus.PAID));
            updateCompensationMetadata(refreshed, "settled", null, null, traceId, trigger, operator);
            log.info(
                    "Payment compensation audit. traceId={} trigger={} operator={} shopId={} paymentId={} paymentNo={} orderId={} reservationNo={} result={} paymentStatus={}",
                    normalizeTraceId(traceId),
                    trigger,
                    normalizeOperator(operator),
                    refreshed.shopId(),
                    refreshed.id(),
                    refreshed.paymentNo(),
                    refreshed.orderId(),
                    refreshed.reservationNo(),
                    "settled",
                    refreshed.status().value()
            );
            return new PaymentCompensationExecution(
                    paymentRepository.findByPaymentNo(refreshed.shopId(), refreshed.paymentNo()).orElse(refreshed),
                    "settled",
                    null,
                    null
            );
        } catch (SanguiException exception) {
            if (exception.errorCode().code().equals(PaymentErrorCode.PAYMENT_ORDER_STATUS_INVALID.code())) {
                paymentRepository.updatePaymentStatus(latest.shopId(), latest.id(), PaymentStatus.FAILED);
            }
            return recordFailed(latest, traceId, trigger, operator, errorCode(exception), sanitizeMessage(exception), exception);
        } catch (RuntimeException exception) {
            return recordFailed(latest, traceId, trigger, operator, errorCode(exception), sanitizeMessage(exception), exception);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String trimmed = traceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof SanguiException sanguiException) {
            return sanguiException.errorCode().code();
        }
        return "INTERNAL_ERROR";
    }

    private PaymentCompensationExecution recordSkipped(
            PaymentOrderRecord payment,
            String traceId,
            String trigger,
            String operator,
            String errorCode,
            String reason
    ) {
        updateCompensationMetadata(payment, "skipped", errorCode, reason, traceId, trigger, operator);
        PaymentOrderRecord refreshed = paymentRepository.findByPaymentNo(payment.shopId(), payment.paymentNo()).orElse(payment);
        log.info(
                "Payment compensation audit. traceId={} trigger={} operator={} shopId={} paymentId={} paymentNo={} orderId={} reservationNo={} result={} errorCode={} reason={} paymentStatus={}",
                normalizeTraceId(traceId),
                trigger,
                normalizeOperator(operator),
                refreshed.shopId(),
                refreshed.id(),
                refreshed.paymentNo(),
                refreshed.orderId(),
                refreshed.reservationNo(),
                "skipped",
                errorCode,
                reason,
                refreshed.status().value()
        );
        return new PaymentCompensationExecution(refreshed, "skipped", errorCode, reason);
    }

    private PaymentCompensationExecution recordFailed(
            PaymentOrderRecord payment,
            String traceId,
            String trigger,
            String operator,
            String errorCode,
            String reason,
            RuntimeException exception
    ) {
        updateCompensationMetadata(payment, "failed", errorCode, reason, traceId, trigger, operator);
        PaymentOrderRecord refreshed = paymentRepository.findByPaymentNo(payment.shopId(), payment.paymentNo()).orElse(payment);
        log.warn(
                "Payment compensation audit. traceId={} trigger={} operator={} shopId={} paymentId={} paymentNo={} orderId={} reservationNo={} result={} errorType={} errorCode={} reason={} paymentStatus={}",
                normalizeTraceId(traceId),
                trigger,
                normalizeOperator(operator),
                refreshed.shopId(),
                refreshed.id(),
                refreshed.paymentNo(),
                refreshed.orderId(),
                refreshed.reservationNo(),
                "failed",
                exception.getClass().getSimpleName(),
                errorCode,
                reason,
                refreshed.status().value()
        );
        return new PaymentCompensationExecution(refreshed, "failed", errorCode, reason);
    }

    private void updateCompensationMetadata(
            PaymentOrderRecord payment,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator
    ) {
        String normalizedTraceId = normalizeTraceId(traceId);
        String normalizedOperator = normalizeOperator(operator);
        paymentRepository.updateCompensationMetadata(
                payment.shopId(),
                payment.id(),
                result,
                errorCode,
                reason,
                normalizedTraceId,
                trigger,
                normalizedOperator,
                LocalDateTime.now(clock)
        );
        paymentRepository.appendCompensationAttempt(
                payment.shopId(),
                payment.id(),
                payment.orderId(),
                payment.paymentNo(),
                payment.orderNo(),
                payment.reservationNo(),
                result,
                errorCode,
                reason,
                normalizedTraceId,
                trigger,
                normalizedOperator
        );
    }

    private String sanitizeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "";
        }
        return message.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String normalizeOperator(String operator) {
        if (operator == null) {
            return null;
        }
        String trimmed = operator.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
