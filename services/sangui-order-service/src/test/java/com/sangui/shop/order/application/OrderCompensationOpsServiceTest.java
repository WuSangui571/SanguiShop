package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.api.dto.OrderCompensationQueryRequest;
import com.sangui.shop.order.api.dto.OrderCompensationQueryResponse;
import com.sangui.shop.order.domain.OrderCompensationAttemptQuery;
import com.sangui.shop.order.domain.OrderCompensationAttemptRecord;
import com.sangui.shop.order.domain.OrderCompensationAttemptSummary;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCompensationOpsServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-03T06:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    private InMemoryOrderRepository orderRepository;
    private OrderCompensationOpsService orderCompensationOpsService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        orderCompensationOpsService = new OrderCompensationOpsService(
                orderRepository,
                null,
                new OrderCompensationMetricsRecorder(new SimpleMeterRegistry()),
                FIXED_CLOCK
        );
    }

    @Test
    void queryRecordsReturnsLatestSnapshotAndAllAttemptsForMatchedOrder() {
        orderRepository.seedOrder(
                101L,
                OrderStatus.CANCELLED,
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "inventory release timeout",
                "trace-latest",
                "scheduler",
                null,
                LocalDateTime.of(2026, 5, 3, 12, 10)
        );
        orderRepository.seedAttempt(
                1L,
                101L,
                "ORD-001",
                "ord:10001:req-001",
                "skipped",
                "ORDER_NOT_TIMEOUT_ELIGIBLE",
                "not yet timed out",
                "trace-old",
                "manual",
                "ops-a",
                LocalDateTime.of(2026, 5, 3, 12, 5)
        );
        orderRepository.seedAttempt(
                2L,
                101L,
                "ORD-001",
                "ord:10001:req-001",
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "inventory release timeout",
                "trace-latest",
                "scheduler",
                null,
                LocalDateTime.of(2026, 5, 3, 12, 10)
        );

        OrderCompensationQueryResponse response = orderCompensationOpsService.queryRecords(new OrderCompensationQueryRequest(
                1L,
                101L,
                null,
                "failed",
                null,
                null,
                null,
                null,
                1,
                10
        ));

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).order().orderId()).isEqualTo(101L);
        assertThat(response.items().get(0).matchedAttemptCount()).isEqualTo(1L);
        assertThat(response.items().get(0).totalAttemptCount()).isEqualTo(2L);
        assertThat(response.items().get(0).attempts()).hasSize(2);
        assertThat(response.items().get(0).attempts().get(0).result()).isEqualTo("failed");
        assertThat(response.items().get(0).attempts().get(1).result()).isEqualTo("skipped");
    }

    @Test
    void queryRecordsPagesDistinctOrdersByLatestMatchedAttemptTime() {
        orderRepository.seedOrder(101L, OrderStatus.CANCELLED, "failed", null, null, "trace-101", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 8));
        orderRepository.seedOrder(102L, OrderStatus.CANCELLED, "failed", null, null, "trace-102", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 12));
        orderRepository.seedAttempt(1L, 101L, "ORD-001", "ord:10001:req-001", "failed", null, null, "trace-101", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 8));
        orderRepository.seedAttempt(2L, 102L, "ORD-002", "ord:10001:req-002", "failed", null, null, "trace-102", "scheduler", null, LocalDateTime.of(2026, 5, 3, 12, 12));

        OrderCompensationQueryResponse response = orderCompensationOpsService.queryRecords(new OrderCompensationQueryRequest(
                1L,
                null,
                null,
                "failed",
                null,
                null,
                null,
                null,
                2,
                1
        ));

        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.pageNo()).isEqualTo(2);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).order().orderId()).isEqualTo(101L);
    }

    @Test
    void queryRecordsRejectsInvalidTimeRange() {
        assertThatThrownBy(() -> orderCompensationOpsService.queryRecords(new OrderCompensationQueryRequest(
                1L,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                1,
                10
        )))
                .isInstanceOf(SanguiException.class)
                .satisfies(exception -> {
                    SanguiException sanguiException = (SanguiException) exception;
                    assertThat(sanguiException.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
                    assertThat(sanguiException.httpStatus()).isEqualTo(400);
                });
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<Long, OrderRecord> ordersById = new LinkedHashMap<>();
        private final List<OrderCompensationAttemptRecord> attempts = new ArrayList<>();

        @Override
        public Optional<OrderRecord> findById(Long shopId, Long orderId) {
            OrderRecord order = ordersById.get(orderId);
            if (order == null || !order.shopId().equals(shopId)) {
                return Optional.empty();
            }
            return Optional.of(order);
        }

        @Override
        public Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId) {
            return Optional.empty();
        }

        @Override
        public List<OrderRecord> findExpiredCreatedOrders(Long shopId, LocalDateTime createdBefore, int limit) {
            return List.of();
        }

        @Override
        public List<OrderRecord> findCancelledOrders(Long shopId, int limit) {
            return List.of();
        }

        @Override
        public long countCompensationAttempts(OrderCompensationAttemptQuery query) {
            return filteredAttempts(query).stream()
                    .map(OrderCompensationAttemptRecord::orderId)
                    .distinct()
                    .count();
        }

        @Override
        public List<OrderCompensationAttemptSummary> findCompensationAttemptSummaries(
                OrderCompensationAttemptQuery query,
                int offset,
                int limit
        ) {
            return filteredAttempts(query).stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            OrderCompensationAttemptRecord::orderId,
                            LinkedHashMap::new,
                            java.util.stream.Collectors.toList()
                    ))
                    .entrySet()
                    .stream()
                    .map(entry -> new OrderCompensationAttemptSummary(
                            entry.getKey(),
                            entry.getValue().stream()
                                    .map(OrderCompensationAttemptRecord::createdAt)
                                    .max(LocalDateTime::compareTo)
                                    .orElseThrow(),
                            entry.getValue().size()
                    ))
                    .sorted(Comparator.comparing(OrderCompensationAttemptSummary::latestAttemptAt)
                            .reversed()
                            .thenComparing(OrderCompensationAttemptSummary::orderId, Comparator.reverseOrder()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<OrderCompensationAttemptRecord> findCompensationAttemptsByOrderIds(Long shopId, List<Long> orderIds) {
            return attempts.stream()
                    .filter(attempt -> attempt.shopId().equals(shopId))
                    .filter(attempt -> orderIds.contains(attempt.orderId()))
                    .sorted(Comparator.comparing(OrderCompensationAttemptRecord::orderId)
                            .thenComparing(OrderCompensationAttemptRecord::createdAt, Comparator.reverseOrder())
                            .thenComparing(OrderCompensationAttemptRecord::id, Comparator.reverseOrder()))
                    .toList();
        }

        @Override
        public Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
            return 0;
        }

        @Override
        public void updateCompensationMetadata(
                Long shopId,
                Long orderId,
                String result,
                String errorCode,
                String reason,
                String traceId,
                String trigger,
                String operator,
                LocalDateTime compensatedAt
        ) {
        }

        @Override
        public void appendCompensationAttempt(
                Long shopId,
                Long orderId,
                String orderNo,
                String reservationNo,
                String result,
                String errorCode,
                String reason,
                String traceId,
                String trigger,
                String operator
        ) {
        }

        private void seedOrder(
                Long orderId,
                OrderStatus status,
                String lastCompensationResult,
                String lastCompensationErrorCode,
                String lastCompensationReason,
                String lastCompensationTraceId,
                String lastCompensationTrigger,
                String lastCompensationOperator,
                LocalDateTime lastCompensatedAt
        ) {
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 3, 12, 0);
            ordersById.put(orderId, new OrderRecord(
                    orderId,
                    1L,
                    "10001",
                    "ORD-" + orderId,
                    "req-" + orderId,
                    "ord:10001:req-" + orderId,
                    status,
                    59900L,
                    "trace-order-" + orderId,
                    createdAt,
                    createdAt,
                    lastCompensationResult,
                    lastCompensationErrorCode,
                    lastCompensationReason,
                    lastCompensationTraceId,
                    lastCompensationTrigger,
                    lastCompensationOperator,
                    lastCompensatedAt
            ));
        }

        private void seedAttempt(
                Long attemptId,
                Long orderId,
                String orderNo,
                String reservationNo,
                String result,
                String errorCode,
                String reason,
                String traceId,
                String trigger,
                String operator,
                LocalDateTime createdAt
        ) {
            attempts.add(new OrderCompensationAttemptRecord(
                    attemptId,
                    1L,
                    orderId,
                    orderNo,
                    reservationNo,
                    result,
                    errorCode,
                    reason,
                    traceId,
                    trigger,
                    operator,
                    createdAt,
                    createdAt
            ));
        }

        private List<OrderCompensationAttemptRecord> filteredAttempts(OrderCompensationAttemptQuery query) {
            return attempts.stream()
                    .filter(attempt -> attempt.shopId().equals(query.shopId()))
                    .filter(attempt -> query.orderId() == null || attempt.orderId().equals(query.orderId()))
                    .filter(attempt -> query.trigger() == null || attempt.trigger().equals(query.trigger()))
                    .filter(attempt -> query.result() == null || attempt.result().equals(query.result()))
                    .filter(attempt -> query.operator() == null || java.util.Objects.equals(attempt.operator(), query.operator()))
                    .filter(attempt -> query.traceId() == null || java.util.Objects.equals(attempt.traceId(), query.traceId()))
                    .filter(attempt -> query.fromTime() == null || !attempt.createdAt().isBefore(query.fromTime()))
                    .filter(attempt -> query.toTime() == null || !attempt.createdAt().isAfter(query.toTime()))
                    .toList();
        }
    }
}
