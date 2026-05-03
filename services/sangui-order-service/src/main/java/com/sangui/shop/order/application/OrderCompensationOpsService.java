package com.sangui.shop.order.application;

import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayRequest;
import com.sangui.shop.order.api.dto.ManualOrderTimeoutReplayResponse;
import com.sangui.shop.order.api.dto.OrderCompensationQueryRequest;
import com.sangui.shop.order.api.dto.OrderCompensationQueryResponse;
import com.sangui.shop.order.api.dto.OrderCompensationRecordResponse;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
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
                "Starting manual order timeout replay. traceId={} shopId={} orderId={} timeoutMinutes={}",
                traceId,
                request.shopId(),
                request.orderId(),
                request.timeoutMinutes()
        );
        try {
            OrderTimeoutReplayExecution execution = orderTimeoutCancelService.replayTimeoutOrder(
                    request.shopId(),
                    request.orderId(),
                    request.timeoutMinutes(),
                    traceId,
                    "manual"
            );
            metricsRecorder.incrementRun("success");
            metricsRecorder.incrementItem(execution.result(), 1);
            log.info(
                    "Completed manual order timeout replay. traceId={} shopId={} orderId={} result={} durationMs={}",
                    traceId,
                    request.shopId(),
                    request.orderId(),
                    execution.result(),
                    elapsedMillis(startedAt)
            );
            return new ManualOrderTimeoutReplayResponse(
                    execution.result(),
                    execution.errorCode(),
                    execution.reason(),
                    toResponse(execution.order())
            );
        } catch (RuntimeException exception) {
            metricsRecorder.incrementRun("failed");
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

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
