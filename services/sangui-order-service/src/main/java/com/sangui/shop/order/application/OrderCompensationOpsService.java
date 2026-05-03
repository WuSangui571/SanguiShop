package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayRequest;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayItemResponse;
import com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayRequest;
import com.sangui.shop.order.api.dto.BulkOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.OrderCompensationQueryRequest;
import com.sangui.shop.order.api.dto.OrderCompensationQueryResponse;
import com.sangui.shop.order.api.dto.OrderCompensationRecordResponse;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderStatus;
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
public class OrderCompensationOpsService {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensationOpsService.class);
    private static final int DEFAULT_TIMEOUT_MINUTES = 15;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final OrderRepository orderRepository;
    private final OrderTimeoutCancelService orderTimeoutCancelService;
    private final OrderCompensationMetricsRecorder metricsRecorder;
    private final Clock clock;

    @Autowired
    public OrderCompensationOpsService(
            OrderRepository orderRepository,
            OrderTimeoutCancelService orderTimeoutCancelService,
            OrderCompensationMetricsRecorder metricsRecorder
    ) {
        this(orderRepository, orderTimeoutCancelService, metricsRecorder, Clock.systemDefaultZone());
    }

    OrderCompensationOpsService(
            OrderRepository orderRepository,
            OrderTimeoutCancelService orderTimeoutCancelService,
            OrderCompensationMetricsRecorder metricsRecorder,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderTimeoutCancelService = orderTimeoutCancelService;
        this.metricsRecorder = metricsRecorder;
        this.clock = clock;
    }

    public OrderCompensationQueryResponse queryRecords(OrderCompensationQueryRequest request) {
        int limit = normalizeLimit(request.limit());
        int timeoutMinutes = request.timeoutMinutes() == null ? DEFAULT_TIMEOUT_MINUTES : request.timeoutMinutes();
        LocalDateTime createdBefore = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
        List<OrderCompensationRecordResponse> timeoutOrders = orderRepository.findExpiredCreatedOrders(
                        request.shopId(),
                        createdBefore,
                        limit
                ).stream()
                .map(this::toResponse)
                .toList();
        List<OrderCompensationRecordResponse> cancelledOrders = orderRepository.findCancelledOrders(request.shopId(), limit)
                .stream()
                .map(this::toResponse)
                .toList();
        return new OrderCompensationQueryResponse(request.shopId(), timeoutOrders, cancelledOrders);
    }

    public ManualOrderTimeoutReplayResponse manualReplay(ManualOrderTimeoutReplayRequest request, String traceId) {
        long startedAt = System.nanoTime();
        log.info(
                "Starting manual order timeout replay. traceId={} shopId={} orderId={} timeoutMinutes={} operator={}",
                traceId,
                request.shopId(),
                request.orderId(),
                request.timeoutMinutes(),
                request.operator()
        );
        try {
            OrderTimeoutReplayExecution execution = orderTimeoutCancelService.replayTimeoutOrder(
                    request.shopId(),
                    request.orderId(),
                    request.timeoutMinutes(),
                    traceId,
                    "manual",
                    request.operator()
            );
            metricsRecorder.incrementRun("manual", "success");
            metricsRecorder.incrementItem("manual", execution.result(), 1);
            log.info(
                    "Completed manual order timeout replay. traceId={} shopId={} orderId={} result={} durationMs={} operator={}",
                    traceId,
                    request.shopId(),
                    request.orderId(),
                    execution.result(),
                    elapsedMillis(startedAt),
                    request.operator()
            );
            return new ManualOrderTimeoutReplayResponse(
                    execution.result(),
                    execution.errorCode(),
                    execution.reason(),
                    toResponse(execution.order())
            );
        } catch (RuntimeException exception) {
            metricsRecorder.incrementRun("manual", "failed");
            throw exception;
        }
    }

    public BulkOrderTimeoutReplayResponse bulkReplay(BulkOrderTimeoutReplayRequest request, String traceId) {
        validateBulkRequest(request);
        int limit = normalizeLimit(request.limit());
        int timeoutMinutes = request.timeoutMinutes() == null ? DEFAULT_TIMEOUT_MINUTES : request.timeoutMinutes();
        List<OrderRecord> candidates = resolveBulkCandidates(request.shopId(), timeoutMinutes, limit, request.orderIds());
        long startedAt = System.nanoTime();
        log.info(
                "Starting bulk order timeout replay. traceId={} shopId={} dryRun={} timeoutMinutes={} limit={} operator={} explicitOrderIds={}",
                traceId,
                request.shopId(),
                request.dryRun(),
                timeoutMinutes,
                limit,
                request.operator(),
                request.orderIds() == null ? 0 : request.orderIds().size()
        );
        List<BulkOrderTimeoutReplayItemResponse> items = new ArrayList<>();
        int executedCount = 0;
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        try {
            for (OrderRecord candidate : candidates) {
                if (Boolean.TRUE.equals(request.dryRun())) {
                    BulkOrderTimeoutReplayItemResponse preview = preview(candidate, timeoutMinutes);
                    items.add(preview);
                    if (Objects.equals(preview.result(), "skipped")) {
                        skippedCount++;
                    }
                    continue;
                }
                OrderTimeoutReplayExecution execution = orderTimeoutCancelService.replayTimeoutOrder(
                        candidate.shopId(),
                        candidate.id(),
                        timeoutMinutes,
                        traceId,
                        "manual",
                        request.operator()
                );
                executedCount++;
                items.add(new BulkOrderTimeoutReplayItemResponse(
                        execution.result(),
                        execution.errorCode(),
                        execution.reason(),
                        toResponse(execution.order())
                ));
                if (Objects.equals(execution.result(), "cancelled")) {
                    successCount++;
                } else if (Objects.equals(execution.result(), "skipped")) {
                    skippedCount++;
                } else {
                    failedCount++;
                }
            }
            if (!Boolean.TRUE.equals(request.dryRun())) {
                metricsRecorder.incrementRun("manual", "success");
                metricsRecorder.incrementItem("manual", "cancelled", successCount);
                metricsRecorder.incrementItem("manual", "skipped", skippedCount);
                metricsRecorder.incrementItem("manual", "failed", failedCount);
            }
            log.info(
                    "Completed bulk order timeout replay. traceId={} shopId={} dryRun={} matchedCount={} executedCount={} successCount={} skippedCount={} failedCount={} durationMs={} operator={}",
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
            return new BulkOrderTimeoutReplayResponse(
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

    private OrderCompensationRecordResponse toResponse(OrderRecord order) {
        return new OrderCompensationRecordResponse(
                order.id(),
                order.orderNo(),
                order.userId(),
                order.reservationNo(),
                order.status().value(),
                order.totalAmountCent(),
                order.traceId(),
                toOffsetDateTime(order.createdAt()),
                toOffsetDateTime(order.updatedAt()),
                order.lastCompensationResult(),
                order.lastCompensationErrorCode(),
                order.lastCompensationReason(),
                order.lastCompensationTraceId(),
                order.lastCompensationTrigger(),
                order.lastCompensationOperator(),
                toOffsetDateTime(order.lastCompensatedAt())
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

    private void validateBulkRequest(BulkOrderTimeoutReplayRequest request) {
        boolean hasExplicitIds = request.orderIds() != null && !request.orderIds().isEmpty();
        if (!hasExplicitIds && request.timeoutMinutes() == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        if (hasExplicitIds && request.orderIds().size() > MAX_LIMIT) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private List<OrderRecord> resolveBulkCandidates(Long shopId, int timeoutMinutes, int limit, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            LocalDateTime createdBefore = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
            return orderRepository.findExpiredCreatedOrders(shopId, createdBefore, limit);
        }
        return orderIds.stream()
                .distinct()
                .limit(limit)
                .map(orderId -> orderRepository.findById(shopId, orderId).orElseThrow(() -> new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400)))
                .toList();
    }

    private BulkOrderTimeoutReplayItemResponse preview(OrderRecord order, int timeoutMinutes) {
        if (order.status() != OrderStatus.CREATED) {
            return new BulkOrderTimeoutReplayItemResponse(
                    "skipped",
                    "ORDER_STATUS_NOT_CREATED",
                    "Order is no longer in created status.",
                    toResponse(order)
            );
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
        if (order.createdAt().isAfter(cutoff)) {
            return new BulkOrderTimeoutReplayItemResponse(
                    "skipped",
                    "ORDER_NOT_TIMEOUT_ELIGIBLE",
                    "Order has not reached the timeout threshold yet.",
                    toResponse(order)
            );
        }
        return new BulkOrderTimeoutReplayItemResponse("would-cancel", null, null, toResponse(order));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
