package com.sangui.shop.order.application;

import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCancelService {

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;

    public OrderCancelService(OrderRepository orderRepository, ProductCatalogClient productCatalogClient) {
        this.orderRepository = orderRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Transactional
    public OrderResponse cancelOrder(SanguiPrincipal principal, Long orderId, String traceId) {
        OrderSnapshot snapshot = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
        if (snapshot.order().status() == OrderStatus.CANCELLED) {
            return OrderResponseMapper.toResponse(snapshot);
        }
        if (snapshot.order().status() != OrderStatus.CREATED) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }

        productCatalogClient.releaseInventory(principal.shopId(), snapshot.order().reservationNo(), normalizeTraceId(traceId));
        int updated = orderRepository.updateStatus(principal.shopId(), orderId, OrderStatus.CREATED, OrderStatus.CANCELLED);
        if (updated == 0) {
            OrderSnapshot latest = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
            if (latest.order().status() == OrderStatus.CANCELLED) {
                return OrderResponseMapper.toResponse(latest);
            }
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }
        return OrderResponseMapper.toResponse(requireOwnedOrder(principal.shopId(), principal.userId(), orderId));
    }

    private OrderSnapshot requireOwnedOrder(Long shopId, String userId, Long orderId) {
        OrderSnapshot snapshot = orderRepository.findSnapshotById(shopId, orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        OrderRecord order = snapshot.order();
        if (!Objects.equals(order.userId(), userId)) {
            throw new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404);
        }
        return snapshot;
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String trimmed = traceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
