package com.sangui.shop.order.application;

import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersRequest;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutCompensationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCompensationScheduler.class);

    private final OrderTimeoutCancelService orderTimeoutCancelService;
    private final OrderCompensationMetricsRecorder metricsRecorder;
    private final boolean enabled;
    private final Long shopId;
    private final int timeoutMinutes;
    private final int limit;

    public OrderTimeoutCompensationScheduler(
            OrderTimeoutCancelService orderTimeoutCancelService,
            OrderCompensationMetricsRecorder metricsRecorder,
            @Value("${sangui.compensation.order-timeout.enabled:false}") boolean enabled,
            @Value("${sangui.compensation.order-timeout.shop-id:${sangui.shop.default-shop-id}}") Long shopId,
            @Value("${sangui.compensation.order-timeout.timeout-minutes:15}") int timeoutMinutes,
            @Value("${sangui.compensation.order-timeout.limit:100}") int limit
    ) {
        this.orderTimeoutCancelService = orderTimeoutCancelService;
        this.metricsRecorder = metricsRecorder;
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
            metricsRecorder.incrementRun("disabled");
            return;
        }

        String traceId = "order-timeout-job-" + UUID.randomUUID();
        long startedAt = System.nanoTime();
        MDC.put(TraceConstants.TRACE_ID, traceId);
        try {
            log.info(
                    "Starting order timeout compensation batch. jobName={} traceId={} shopId={} timeoutMinutes={} limit={}",
                    OrderCompensationMetricsRecorder.JOB_NAME,
                    traceId,
                    shopId,
                    timeoutMinutes,
                    limit
            );
            CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(
                    new CancelExpiredOrdersRequest(shopId, timeoutMinutes, limit),
                    traceId
            );
            metricsRecorder.incrementRun("success");
            metricsRecorder.incrementItem("scanned", response.scannedCount());
            metricsRecorder.incrementItem("cancelled", response.cancelledCount());
            metricsRecorder.incrementItem("skipped", response.skippedCount());
            metricsRecorder.incrementItem("failed", response.failedCount());
            log.info(
                    "Completed order timeout compensation batch. jobName={} traceId={} shopId={} timeoutMinutes={} limit={} durationMs={} scannedCount={} cancelledCount={} skippedCount={} failedCount={}",
                    OrderCompensationMetricsRecorder.JOB_NAME,
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
            metricsRecorder.incrementRun("failed");
            log.error(
                    "Order timeout compensation batch failed. jobName={} traceId={} shopId={} timeoutMinutes={} limit={} durationMs={} errorType={} errorCode={} message={}",
                    OrderCompensationMetricsRecorder.JOB_NAME,
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
