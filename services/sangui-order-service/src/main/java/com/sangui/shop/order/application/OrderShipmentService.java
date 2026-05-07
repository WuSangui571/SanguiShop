package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.dto.ConfirmOrderShipmentRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderDetailRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderPageResponse;
import com.sangui.shop.order.client.dto.FulfillmentOrderQueryRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderResponse;
import com.sangui.shop.order.domain.FulfillmentOrderQuery;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderShipmentService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final OrderRepository orderRepository;

    public OrderShipmentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public FulfillmentOrderPageResponse queryFulfillmentRecords(FulfillmentOrderQueryRequest request) {
        validateTimeRange(request.fromTime(), request.toTime());
        int page = normalizePage(request.page());
        int size = normalizeSize(request.size());
        FulfillmentOrderQuery query = new FulfillmentOrderQuery(
                request.shopId(),
                normalizeStatus(request.fulfillmentStatus()),
                trimToNull(request.orderNo()),
                trimToNull(request.userId()),
                toLocalDateTime(request.fromTime()),
                toLocalDateTime(request.toTime())
        );
        int offset = (page - 1) * size;
        return new FulfillmentOrderPageResponse(
                page,
                size,
                orderRepository.countFulfillmentOrders(query),
                orderRepository.findFulfillmentSnapshots(query, offset, size).stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public FulfillmentOrderResponse getFulfillmentRecord(FulfillmentOrderDetailRequest request) {
        return toResponse(orderRepository.findSnapshotById(request.shopId(), request.orderId())
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404)));
    }

    @Transactional
    public FulfillmentOrderResponse confirmShipment(ConfirmOrderShipmentRequest request, String traceId) {
        String requestId = requireText(request.requestId());
        String carrier = requireText(request.carrier());
        String trackingNo = requireText(request.trackingNo());
        OrderRecord order = orderRepository.findById(request.shopId(), request.orderId())
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (order.status() == OrderStatus.SHIPPED) {
            if (Objects.equals(order.shipmentRequestId(), requestId)
                    && Objects.equals(order.carrier(), carrier)
                    && Objects.equals(order.trackingNo(), trackingNo)) {
                return toResponse(order);
            }
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }
        if (order.status() != OrderStatus.PAID) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }
        int updated = orderRepository.markShipped(
                request.shopId(),
                request.orderId(),
                requestId,
                carrier,
                trackingNo,
                traceId,
                LocalDateTime.now()
        );
        if (updated == 0) {
            OrderRecord latest = orderRepository.findById(request.shopId(), request.orderId())
                    .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
            if (latest.status() == OrderStatus.SHIPPED
                    && Objects.equals(latest.shipmentRequestId(), requestId)
                    && Objects.equals(latest.carrier(), carrier)
                    && Objects.equals(latest.trackingNo(), trackingNo)) {
                return toResponse(latest);
            }
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }
        return toResponse(orderRepository.findById(request.shopId(), request.orderId())
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404)));
    }

    private FulfillmentOrderResponse toResponse(OrderSnapshot snapshot) {
        return toResponse(snapshot.order());
    }

    private FulfillmentOrderResponse toResponse(OrderRecord order) {
        return new FulfillmentOrderResponse(
                order.id(),
                order.orderNo(),
                order.shopId(),
                order.userId(),
                order.status().value(),
                fulfillmentStatus(order),
                order.totalAmountCent(),
                order.carrier(),
                order.trackingNo(),
                toOffsetDateTime(order.shippedAt()),
                order.traceId(),
                toOffsetDateTime(order.createdAt()),
                toOffsetDateTime(order.updatedAt())
        );
    }

    private String fulfillmentStatus(OrderRecord order) {
        if (order.status() == OrderStatus.SHIPPED) {
            return OrderStatus.SHIPPED.value();
        }
        if (order.status() == OrderStatus.PAID) {
            return "unshipped";
        }
        return order.fulfillmentStatus() == null ? "pending" : order.fulfillmentStatus();
    }

    private int normalizePage(Integer value) {
        return value == null ? DEFAULT_PAGE : Math.max(1, value);
    }

    private int normalizeSize(Integer value) {
        return value == null ? DEFAULT_SIZE : Math.min(Math.max(1, value), MAX_SIZE);
    }

    private String normalizeStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        if ("unshipped".equalsIgnoreCase(normalized) || "shipped".equalsIgnoreCase(normalized)) {
            return normalized.toLowerCase(java.util.Locale.ROOT);
        }
        throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
    }

    private void validateTimeRange(OffsetDateTime fromTime, OffsetDateTime toTime) {
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String requireText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
