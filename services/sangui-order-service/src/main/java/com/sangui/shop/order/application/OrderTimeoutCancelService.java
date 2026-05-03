package com.sangui.shop.order.application;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersRequest;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTimeoutCancelService {

    private static final int DEFAULT_TIMEOUT_MINUTES = 15;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCancelService.class);

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;
    private final Clock clock;

    @Autowired
    public OrderTimeoutCancelService(OrderRepository orderRepository, ProductCatalogClient productCatalogClient) {
        this(orderRepository, productCatalogClient, Clock.systemDefaultZone());
    }

    OrderTimeoutCancelService(OrderRepository orderRepository, ProductCatalogClient productCatalogClient, Clock clock) {
        this.orderRepository = orderRepository;
        this.productCatalogClient = productCatalogClient;
        this.clock = clock;
    }

    @Transactional
    public CancelExpiredOrdersResponse cancelExpiredOrders(CancelExpiredOrdersRequest request, String traceId) {
        int limit = normalizeLimit(request.limit());
        int timeoutMinutes = normalizeTimeoutMinutes(request.timeoutMinutes());
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
        List<OrderRecord> expiredOrders = orderRepository.findExpiredCreatedOrders(request.shopId(), cutoff, limit);

        int cancelledCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        for (OrderRecord order : expiredOrders) {
            OrderTimeoutReplayExecution execution = replayTimeoutOrder(order.shopId(), order.id(), timeoutMinutes, traceId, "scheduler");
            if ("cancelled".equals(execution.result())) {
                cancelledCount++;
            } else if ("skipped".equals(execution.result())) {
                skippedCount++;
            } else {
                failedCount++;
            }
        }
        return new CancelExpiredOrdersResponse(request.shopId(), expiredOrders.size(), cancelledCount, skippedCount, failedCount);
    }

    @Transactional
    public OrderTimeoutReplayExecution replayTimeoutOrder(Long shopId, Long orderId, Integer timeoutMinutes, String traceId, String trigger) {
        OrderRecord latest = orderRepository.findById(shopId, orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (latest.status() != OrderStatus.CREATED) {
            return recordSkipped(latest, traceId, trigger, "ORDER_STATUS_NOT_CREATED", "Order is no longer in created status.");
        }
        int effectiveTimeoutMinutes = normalizeTimeoutMinutes(timeoutMinutes);
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(effectiveTimeoutMinutes);
        if (latest.createdAt().isAfter(cutoff)) {
            return recordSkipped(latest, traceId, trigger, "ORDER_NOT_TIMEOUT_ELIGIBLE", "Order has not reached the timeout threshold yet.");
        }
        try {
            productCatalogClient.releaseInventory(latest.shopId(), latest.reservationNo(), normalizeTraceId(traceId));
            int updated = orderRepository.updateStatus(latest.shopId(), latest.id(), OrderStatus.CREATED, OrderStatus.CANCELLED);
            if (updated > 0) {
                updateCompensationMetadata(latest, "cancelled", null, null, traceId, trigger);
                OrderRecord refreshed = orderRepository.findById(latest.shopId(), latest.id())
                        .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
                log.info(
                        "Order compensation audit. traceId={} trigger={} shopId={} orderId={} orderNo={} reservationNo={} result={} orderStatus={}",
                        normalizeTraceId(traceId),
                        trigger,
                        refreshed.shopId(),
                        refreshed.id(),
                        refreshed.orderNo(),
                        refreshed.reservationNo(),
                        "cancelled",
                        refreshed.status().value()
                );
                return new OrderTimeoutReplayExecution(refreshed, "cancelled", null, null);
            }
            OrderRecord refreshed = orderRepository.findById(latest.shopId(), latest.id())
                    .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
            if (refreshed.status() == OrderStatus.CANCELLED || refreshed.status() == OrderStatus.PAID) {
                return recordSkipped(refreshed, traceId, trigger, "ORDER_STATUS_NOT_CREATED", "Order was already processed by another path.");
            }
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        } catch (RuntimeException exception) {
            return recordFailed(latest, traceId, trigger, errorCode(exception), sanitizeMessage(exception), exception);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeTimeoutMinutes(Integer timeoutMinutes) {
        return timeoutMinutes == null ? DEFAULT_TIMEOUT_MINUTES : timeoutMinutes;
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

    private OrderTimeoutReplayExecution recordSkipped(
            OrderRecord order,
            String traceId,
            String trigger,
            String errorCode,
            String reason
    ) {
        updateCompensationMetadata(order, "skipped", errorCode, reason, traceId, trigger);
        OrderRecord refreshed = orderRepository.findById(order.shopId(), order.id()).orElse(order);
        log.info(
                "Order compensation audit. traceId={} trigger={} shopId={} orderId={} orderNo={} reservationNo={} result={} errorCode={} reason={} orderStatus={}",
                normalizeTraceId(traceId),
                trigger,
                refreshed.shopId(),
                refreshed.id(),
                refreshed.orderNo(),
                refreshed.reservationNo(),
                "skipped",
                errorCode,
                reason,
                refreshed.status().value()
        );
        return new OrderTimeoutReplayExecution(refreshed, "skipped", errorCode, reason);
    }

    private OrderTimeoutReplayExecution recordFailed(
            OrderRecord order,
            String traceId,
            String trigger,
            String errorCode,
            String reason,
            RuntimeException exception
    ) {
        updateCompensationMetadata(order, "failed", errorCode, reason, traceId, trigger);
        OrderRecord refreshed = orderRepository.findById(order.shopId(), order.id()).orElse(order);
        log.warn(
                "Order compensation audit. traceId={} trigger={} shopId={} orderId={} orderNo={} reservationNo={} result={} errorType={} errorCode={} reason={} orderStatus={}",
                normalizeTraceId(traceId),
                trigger,
                refreshed.shopId(),
                refreshed.id(),
                refreshed.orderNo(),
                refreshed.reservationNo(),
                "failed",
                exception.getClass().getSimpleName(),
                errorCode,
                reason,
                refreshed.status().value()
        );
        return new OrderTimeoutReplayExecution(refreshed, "failed", errorCode, reason);
    }

    private void updateCompensationMetadata(
            OrderRecord order,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger
    ) {
        orderRepository.updateCompensationMetadata(
                order.shopId(),
                order.id(),
                result,
                errorCode,
                reason,
                normalizeTraceId(traceId),
                trigger,
                LocalDateTime.now(clock)
        );
    }

    private String sanitizeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "";
        }
        return message.replaceAll("[\\r\\n]+", " ").trim();
    }
}
