package com.sangui.shop.order.application;

import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersRequest;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
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
public class OrderTimeoutCompensationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCompensationScheduler.class);
    private static final String JOB_NAME = "order-timeout";
    private static final String RUN_COUNTER = "sangui.compensation.job.run.total";
    private static final String ITEM_COUNTER = "sangui.compensation.job.item.total";

    private final OrderTimeoutCancelService orderTimeoutCancelService;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final Long shopId;
    private final int timeoutMinutes;
    private final int limit;

    public OrderTimeoutCompensationScheduler(
            OrderTimeoutCancelService orderTimeoutCancelService,
            MeterRegistry meterRegistry,
            @Value("${sangui.compensation.order-timeout.enabled:false}") boolean enabled,
            @Value("${sangui.compensation.order-timeout.shop-id:${sangui.shop.default-shop-id}}") Long shopId,
            @Value("${sangui.compensation.order-timeout.timeout-minutes:15}") int timeoutMinutes,
            @Value("${sangui.compensation.order-timeout.limit:100}") int limit
    ) {
        this.orderTimeoutCancelService = orderTimeoutCancelService;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.shopId = shopId;
        this.timeoutMinutes = timeoutMinutes;
        this.limit = limit;
    }

    @Scheduled(
            fixedDelayString = "${sangui.compensation.order-timeout.fixed-delay-ms:60000}",
            initialDelayString = "${sangui.compensation.order-timeout.initial-delay-ms:15000}"
    )
    public void run() {
        if (!enabled) {
            incrementRunCounter("disabled");
            return;
        }

        String traceId = "order-timeout-job-" + UUID.randomUUID();
        long startedAt = System.nanoTime();
        MDC.put(TraceConstants.TRACE_ID, traceId);
        try {
            log.info(
                    "Starting order timeout compensation batch. jobName={} traceId={} shopId={} timeoutMinutes={} limit={}",
                    JOB_NAME,
                    traceId,
                    shopId,
                    timeoutMinutes,
                    limit
            );
            CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(
                    new CancelExpiredOrdersRequest(shopId, timeoutMinutes, limit),
                    traceId
            );
            incrementRunCounter("success");
            incrementItemCounter("scanned", response.scannedCount());
            incrementItemCounter("cancelled", response.cancelledCount());
            incrementItemCounter("skipped", response.skippedCount());
            incrementItemCounter("failed", response.failedCount());
            log.info(
                    "Completed order timeout compensation batch. jobName={} traceId={} shopId={} timeoutMinutes={} limit={} durationMs={} scannedCount={} cancelledCount={} skippedCount={} failedCount={}",
                    JOB_NAME,
                    traceId,
                    response.shopId(),
                    timeoutMinutes,
                    limit,
                    elapsedMillis(startedAt),
                    response.scannedCount(),
                    response.cancelledCount(),
                    response.skippedCount(),
                    response.failedCount()
            );
        } catch (RuntimeException exception) {
            incrementRunCounter("failed");
            log.error(
                    "Order timeout compensation batch failed. jobName={} traceId={} shopId={} timeoutMinutes={} limit={} durationMs={} errorType={} errorCode={} message={}",
                    JOB_NAME,
                    traceId,
                    shopId,
                    timeoutMinutes,
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
                .tag("service", "order")
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
                .tag("service", "order")
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
