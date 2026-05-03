package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.trace.TraceConstants;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileScheduler.class);

    private final PaymentReconcileService paymentReconcileService;
    private final boolean enabled;
    private final Long shopId;
    private final int minAgeMinutes;
    private final int limit;

    public PaymentReconcileScheduler(
            PaymentReconcileService paymentReconcileService,
            @Value("${sangui.compensation.payment-reconcile.enabled:false}") boolean enabled,
            @Value("${sangui.compensation.payment-reconcile.shop-id:${sangui.shop.default-shop-id}}") Long shopId,
            @Value("${sangui.compensation.payment-reconcile.min-age-minutes:1}") int minAgeMinutes,
            @Value("${sangui.compensation.payment-reconcile.limit:100}") int limit
    ) {
        this.paymentReconcileService = paymentReconcileService;
        this.enabled = enabled;
        this.shopId = shopId;
        this.minAgeMinutes = minAgeMinutes;
        this.limit = limit;
    }

    @Scheduled(
            fixedDelayString = "${sangui.compensation.payment-reconcile.fixed-delay-ms:60000}",
            initialDelayString = "${sangui.compensation.payment-reconcile.initial-delay-ms:15000}"
    )
    public void run() {
        if (!enabled) {
            return;
        }

        String traceId = "payment-reconcile-job-" + UUID.randomUUID();
        MDC.put(TraceConstants.TRACE_ID, traceId);
        try {
            log.info(
                    "Starting payment reconcile batch. traceId={} shopId={} minAgeMinutes={} limit={}",
                    traceId,
                    shopId,
                    minAgeMinutes,
                    limit
            );
            PaymentReconcileResult result = paymentReconcileService.reconcileCreatedPayments(shopId, minAgeMinutes, limit, traceId);
            log.info(
                    "Completed payment reconcile batch. traceId={} shopId={} scannedCount={} settledCount={} skippedCount={} failedCount={}",
                    traceId,
                    result.shopId(),
                    result.scannedCount(),
                    result.settledCount(),
                    result.skippedCount(),
                    result.failedCount()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Payment reconcile batch failed. traceId={} shopId={} minAgeMinutes={} limit={}",
                    traceId,
                    shopId,
                    minAgeMinutes,
                    limit,
                    exception
            );
        } finally {
            MDC.remove(TraceConstants.TRACE_ID);
        }
    }
}
