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
            try {
                if (reconcileOne(payment, traceId)) {
                    settledCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn(
                        "Payment reconcile failed. traceId={} shopId={} paymentId={} paymentNo={} orderId={} reservationNo={}",
                        normalizeTraceId(traceId),
                        payment.shopId(),
                        payment.id(),
                        payment.paymentNo(),
                        payment.orderId(),
                        payment.reservationNo(),
                        exception
                );
            }
        }

        return new PaymentReconcileResult(shopId, payments.size(), settledCount, skippedCount, failedCount);
    }

    private boolean reconcileOne(PaymentOrderRecord payment, String traceId) {
        PaymentOrderRecord latest = paymentRepository.findByPaymentNo(payment.shopId(), payment.paymentNo())
                .orElseThrow(() -> new SanguiException(PaymentErrorCode.PAYMENT_NOT_FOUND, 404));
        if (latest.status() != PaymentStatus.CREATED) {
            return false;
        }
        try {
            paymentPayService.settlePayment(latest, traceId);
            return true;
        } catch (SanguiException exception) {
            if (exception.errorCode().code().equals(PaymentErrorCode.PAYMENT_ORDER_STATUS_INVALID.code())) {
                paymentRepository.updatePaymentStatus(latest.shopId(), latest.id(), PaymentStatus.FAILED);
            }
            throw exception;
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
}
