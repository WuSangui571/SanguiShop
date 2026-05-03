package com.sangui.shop.order.application;

import com.sangui.shop.common.core.trace.TraceConstants;
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
    private final boolean enabled;
    private final Long shopId;
    private final int timeoutMinutes;
    private final int limit;

    public OrderTimeoutCompensationScheduler(
            OrderTimeoutCancelService orderTimeoutCancelService,
            @Value("${sangui.compensation.order-timeout.enabled:false}") boolean enabled,
            @Value("${sangui.compensation.order-timeout.shop-id:${sangui.shop.default-shop-id}}") Long shopId,
            @Value("${sangui.compensation.order-timeout.timeout-minutes:15}") int timeoutMinutes,
            @Value("${sangui.compensation.order-timeout.limit:100}") int limit
    ) {
        this.orderTimeoutCancelService = orderTimeoutCancelService;
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
            return;
        }

        String traceId = "order-timeout-job-" + UUID.randomUUID();
        MDC.put(TraceConstants.TRACE_ID, traceId);
        try {
            log.info(
                    "Starting order timeout compensation batch. traceId={} shopId={} timeoutMinutes={} limit={}",
                    traceId,
                    shopId,
                    timeoutMinutes,
                    limit
            );
            CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(
                    new CancelExpiredOrdersRequest(shopId, timeoutMinutes, limit),
                    traceId
            );
            log.info(
                    "Completed order timeout compensation batch. traceId={} shopId={} scannedCount={} cancelledCount={} skippedCount={} failedCount={}",
                    traceId,
                    response.shopId(),
                    response.scannedCount(),
                    response.cancelledCount(),
                    response.skippedCount(),
                    response.failedCount()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Order timeout compensation batch failed. traceId={} shopId={} timeoutMinutes={} limit={}",
                    traceId,
                    shopId,
                    timeoutMinutes,
                    limit,
                    exception
            );
        } finally {
            MDC.remove(TraceConstants.TRACE_ID);
        }
    }
}
