package com.sangui.shop.payment.application;

import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.core.exception.SanguiException;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileScheduler.class);
    private static final String JOB_NAME = "payment-reconcile";
    private static final String RUN_COUNTER = "sangui.compensation.job.run.total";
    private static final String ITEM_COUNTER = "sangui.compensation.job.item.total";

    private final PaymentReconcileService paymentReconcileService;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final Long shopId;
    private final int minAgeMinutes;
    private final int limit;

    public PaymentReconcileScheduler(
            PaymentReconcileService paymentReconcileService,
            MeterRegistry meterRegistry,
            @Value("${sangui.compensation.payment-reconcile.enabled:false}") boolean enabled,
            @Value("${sangui.compensation.payment-reconcile.shop-id:${sangui.shop.default-shop-id}}") Long shopId,
            @Value("${sangui.compensation.payment-reconcile.min-age-minutes:1}") int minAgeMinutes,
            @Value("${sangui.compensation.payment-reconcile.limit:100}") int limit
    ) {
        this.paymentReconcileService = paymentReconcileService;
        this.meterRegistry = meterRegistry;
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
            incrementRunCounter("disabled");
            return;
        }

        String traceId = "payment-reconcile-job-" + UUID.randomUUID();
        long startedAt = System.nanoTime();
        MDC.put(TraceConstants.TRACE_ID, traceId);
        try {
            log.info(
                    "Starting payment reconcile batch. jobName={} traceId={} shopId={} minAgeMinutes={} limit={}",
                    JOB_NAME,
                    traceId,
                    shopId,
                    minAgeMinutes,
                    limit
            );
            PaymentReconcileResult result = paymentReconcileService.reconcileCreatedPayments(shopId, minAgeMinutes, limit, traceId);
            incrementRunCounter("success");
            incrementItemCounter("scanned", result.scannedCount());
            incrementItemCounter("settled", result.settledCount());
            incrementItemCounter("skipped", result.skippedCount());
            incrementItemCounter("failed", result.failedCount());
            log.info(
                    "Completed payment reconcile batch. jobName={} traceId={} shopId={} minAgeMinutes={} limit={} durationMs={} scannedCount={} settledCount={} skippedCount={} failedCount={}",
                    JOB_NAME,
                    traceId,
                    result.shopId(),
                    minAgeMinutes,
                    limit,
                    elapsedMillis(startedAt),
                    result.scannedCount(),
                    result.settledCount(),
                    result.skippedCount(),
                    result.failedCount()
            );
        } catch (RuntimeException exception) {
            incrementRunCounter("failed");
            log.error(
                    "Payment reconcile batch failed. jobName={} traceId={} shopId={} minAgeMinutes={} limit={} durationMs={} errorType={} errorCode={} message={}",
                    JOB_NAME,
                    traceId,
                    shopId,
                    minAgeMinutes,
                    limit,
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(),
                    errorCode(exception),
                    sanitizeMessage(exception)
            );
        } finally {
            MDC.remove(TraceConstants.TRACE_ID);
        }
    }

    private void incrementRunCounter(String result) {
        Counter.builder(RUN_COUNTER)
                .description("Total compensation job runs grouped by result.")
                .tag("service", "payment")
                .tag("job", JOB_NAME)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private void incrementItemCounter(String result, int amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(ITEM_COUNTER)
                .description("Total compensation job items grouped by result.")
                .tag("service", "payment")
                .tag("job", JOB_NAME)
                .tag("result", result)
                .register(meterRegistry)
                .increment(amount);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof SanguiException sanguiException) {
            return sanguiException.errorCode().code();
        }
        return "INTERNAL_ERROR";
    }

    private String sanitizeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "";
        }
        return message.replaceAll("[\\r\\n]+", " ").trim();
    }
}
