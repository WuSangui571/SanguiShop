package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.CreateOrderItemRequest;
import com.sangui.shop.order.api.dto.CreateOrderRequest;
import com.sangui.shop.order.client.InventoryReservationSnapshot;
import com.sangui.shop.order.client.InventoryReserveItemSnapshot;
import com.sangui.shop.order.api.dto.OrderItemResponse;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderItemDraft;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderNumberGenerator;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreateService {

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderCreateService(
            OrderRepository orderRepository,
            ProductCatalogClient productCatalogClient,
            OrderNumberGenerator orderNumberGenerator
    ) {
        this.orderRepository = orderRepository;
        this.productCatalogClient = productCatalogClient;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    @Transactional
    public OrderResponse createOrder(SanguiPrincipal principal, CreateOrderRequest request, String traceId) {
        OrderSnapshot existing = orderRepository.findByRequestId(principal.shopId(), principal.userId(), request.requestId())
                .orElse(null);
        if (existing != null) {
            return ensureIdempotentReplay(existing, request);
        }

        rejectDuplicateSkuIds(request.items());
        String reservationNo = buildReservationNo(principal.userId(), request.requestId());
        OrderCreateDraft draft = buildDraft(principal.shopId(), reservationNo, request, traceId);
        String orderNo = orderNumberGenerator.nextOrderNo();

        try {
            Long orderId = orderRepository.createOrder(
                    principal.shopId(),
                    principal.userId(),
                    orderNo,
                    normalizeTraceId(traceId),
                    OrderStatus.CREATED,
                    draft
            );
            return toResponse(orderId, orderNo, principal.shopId(), principal.userId(), OrderStatus.CREATED, draft);
        } catch (DuplicateKeyException exception) {
            OrderSnapshot duplicated = orderRepository.findByRequestId(principal.shopId(), principal.userId(), request.requestId())
                    .orElseThrow(() -> exception);
            return ensureIdempotentReplay(duplicated, request);
        }
    }

    private OrderResponse ensureIdempotentReplay(OrderSnapshot snapshot, CreateOrderRequest request) {
        if (!matchesRequest(snapshot, request)) {
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }
        return toResponse(snapshot);
    }

    private boolean matchesRequest(OrderSnapshot snapshot, CreateOrderRequest request) {
        if (!Objects.equals(snapshot.order().requestId(), request.requestId())) {
            return false;
        }
        if (snapshot.items().size() != request.items().size()) {
            return false;
        }
        Map<Long, Integer> persistedQuantities = new HashMap<>();
        for (OrderItemRecord item : snapshot.items()) {
            persistedQuantities.put(item.skuId(), item.quantity());
        }
        for (CreateOrderItemRequest item : request.items()) {
            if (!Objects.equals(persistedQuantities.get(item.skuId()), item.quantity())) {
                return false;
            }
        }
        return true;
    }

    private OrderCreateDraft buildDraft(Long shopId, String reservationNo, CreateOrderRequest request, String traceId) {
        InventoryReservationSnapshot reservation = productCatalogClient.reserveInventory(
                shopId,
                reservationNo,
                request.items().stream()
                        .map(item -> new InventoryReserveItemSnapshot(item.skuId(), item.quantity()))
                        .toList(),
                normalizeTraceId(traceId)
        );
        Map<Long, Integer> quantitiesBySkuId = request.items().stream()
                .collect(java.util.stream.Collectors.toMap(CreateOrderItemRequest::skuId, CreateOrderItemRequest::quantity));
        List<OrderItemDraft> items = reservation.items().stream()
                .map(item -> new OrderItemDraft(
                        item.productId(),
                        item.skuId(),
                        item.skuName(),
                        item.priceCent(),
                        quantitiesBySkuId.getOrDefault(item.skuId(), item.quantity())
                ))
                .toList();
        return new OrderCreateDraft(request.requestId().trim(), reservation.reservationNo(), items);
    }

    private void rejectDuplicateSkuIds(List<CreateOrderItemRequest> items) {
        Set<Long> seenSkuIds = new HashSet<>();
        for (CreateOrderItemRequest item : items) {
            if (!seenSkuIds.add(item.skuId())) {
                throw new SanguiException(OrderErrorCode.ORDER_SKU_DUPLICATED, 409);
            }
        }
    }

    private OrderResponse toResponse(OrderSnapshot snapshot) {
        List<OrderItemResponse> items = snapshot.items().stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.skuId(),
                        item.skuName(),
                        item.priceCent(),
                        item.quantity(),
                        item.lineAmountCent()
                ))
                .toList();
        return new OrderResponse(
                snapshot.order().id(),
                snapshot.order().orderNo(),
                snapshot.order().shopId(),
                snapshot.order().userId(),
                snapshot.order().requestId(),
                snapshot.order().status().value(),
                snapshot.order().totalAmountCent(),
                items
        );
    }

    private OrderResponse toResponse(
            Long orderId,
            String orderNo,
            Long shopId,
            String userId,
            OrderStatus status,
            OrderCreateDraft draft
    ) {
        List<OrderItemResponse> items = draft.items().stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.skuId(),
                        item.skuName(),
                        item.priceCent(),
                        item.quantity(),
                        item.lineAmountCent()
                ))
                .toList();
        return new OrderResponse(
                orderId,
                orderNo,
                shopId,
                userId,
                draft.requestId(),
                status.value(),
                draft.totalAmountCent(),
                items
        );
    }

    private String buildReservationNo(String userId, String requestId) {
        return "ord:" + userId + ":" + requestId.trim();
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String trimmed = traceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
