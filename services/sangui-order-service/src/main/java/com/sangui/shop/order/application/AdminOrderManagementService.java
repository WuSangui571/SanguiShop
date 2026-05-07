package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.AdminCancelOrderRequest;
import com.sangui.shop.order.api.dto.AdminOrderDetailResponse;
import com.sangui.shop.order.api.dto.AdminOrderPageResponse;
import com.sangui.shop.order.api.dto.AdminOrderStatusTimelineResponse;
import com.sangui.shop.order.api.dto.AdminOrderSummaryResponse;
import com.sangui.shop.order.api.dto.OrderItemResponse;
import com.sangui.shop.order.domain.AdminOrderQuery;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderManagementService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderCancelService orderCancelService;

    public AdminOrderManagementService(OrderRepository orderRepository, OrderCancelService orderCancelService) {
        this.orderRepository = orderRepository;
        this.orderCancelService = orderCancelService;
    }

    @Transactional(readOnly = true)
    public AdminOrderPageResponse listOrders(
            SanguiPrincipal principal,
            int page,
            int size,
            String status,
            String orderNo,
            String userId,
            LocalDateTime fromTime,
            LocalDateTime toTime
    ) {
        requireAdmin(principal);
        validateTimeRange(fromTime, toTime);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        AdminOrderQuery query = new AdminOrderQuery(
                principal.shopId(),
                parseOptionalStatus(status),
                trimToNull(orderNo),
                trimToNull(userId),
                fromTime,
                toTime
        );
        int offset = (normalizedPage - 1) * normalizedSize;
        return new AdminOrderPageResponse(
                normalizedPage,
                normalizedSize,
                orderRepository.countAdminOrders(query),
                orderRepository.findAdminSnapshots(query, offset, normalizedSize).stream()
                        .map(this::toSummaryResponse)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrder(SanguiPrincipal principal, Long orderId) {
        requireAdmin(principal);
        OrderSnapshot snapshot = orderRepository.findSnapshotById(principal.shopId(), orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        return toDetailResponse(snapshot);
    }

    @Transactional
    public AdminOrderDetailResponse cancelOrder(
            SanguiPrincipal principal,
            Long orderId,
            AdminCancelOrderRequest request,
            String traceId
    ) {
        requireAdmin(principal);
        if (request == null || trimToNull(request.requestId()) == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        orderCancelService.cancelOrderForShop(principal.shopId(), orderId, traceId);
        OrderSnapshot snapshot = orderRepository.findSnapshotById(principal.shopId(), orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        return toDetailResponse(snapshot);
    }

    private void requireAdmin(SanguiPrincipal principal) {
        boolean hasAdminRole = principal.roles() != null && principal.roles().contains(ADMIN_ROLE);
        boolean hasOrderAdminPermission = principal.permissions() != null
                && principal.permissions().contains(SanguiPermissionConstants.ORDER_MANAGEMENT_ADMIN);
        if (!hasAdminRole && !hasOrderAdminPermission) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
    }

    private void validateTimeRange(LocalDateTime fromTime, LocalDateTime toTime) {
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private OrderStatus parseOptionalStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return OrderStatus.fromValue(normalized);
        } catch (IllegalArgumentException exception) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }
    }

    private AdminOrderSummaryResponse toSummaryResponse(OrderSnapshot snapshot) {
        OrderRecord order = snapshot.order();
        return new AdminOrderSummaryResponse(
                order.id(),
                order.orderNo(),
                order.shopId(),
                order.userId(),
                order.status().value(),
                order.totalAmountCent(),
                null,
                snapshot.items().stream().mapToInt(OrderItemRecord::quantity).sum(),
                order.traceId(),
                toOffsetDateTime(order.createdAt()),
                toOffsetDateTime(order.updatedAt())
        );
    }

    private AdminOrderDetailResponse toDetailResponse(OrderSnapshot snapshot) {
        OrderRecord order = snapshot.order();
        return new AdminOrderDetailResponse(
                order.id(),
                order.orderNo(),
                order.shopId(),
                order.userId(),
                order.requestId(),
                order.reservationNo(),
                null,
                order.status().value(),
                order.totalAmountCent(),
                order.traceId(),
                toOffsetDateTime(order.createdAt()),
                toOffsetDateTime(order.updatedAt()),
                snapshot.items().stream().map(this::toItemResponse).toList(),
                buildTimeline(order)
        );
    }

    private OrderItemResponse toItemResponse(OrderItemRecord item) {
        return new OrderItemResponse(
                item.productId(),
                item.skuId(),
                item.skuName(),
                item.priceCent(),
                item.quantity(),
                item.lineAmountCent()
        );
    }

    private List<AdminOrderStatusTimelineResponse> buildTimeline(OrderRecord order) {
        List<AdminOrderStatusTimelineResponse> timeline = new ArrayList<>();
        timeline.add(new AdminOrderStatusTimelineResponse(
                OrderStatus.CREATED.value(),
                toOffsetDateTime(order.createdAt()),
                order.traceId()
        ));
        if (order.status() != OrderStatus.CREATED) {
            timeline.add(new AdminOrderStatusTimelineResponse(
                    order.status().value(),
                    toOffsetDateTime(order.updatedAt()),
                    order.traceId()
            ));
        }
        return timeline;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
