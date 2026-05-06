package com.sangui.shop.order.application;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.OrderPageResponse;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(SanguiPrincipal principal, Long orderId) {
        return OrderResponseMapper.toResponse(requireOwnedOrder(principal.shopId(), principal.userId(), orderId));
    }

    @Transactional(readOnly = true)
    public OrderPageResponse listOrders(SanguiPrincipal principal, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;
        return new OrderPageResponse(
                normalizedPage,
                normalizedSize,
                orderRepository.countByUser(principal.shopId(), principal.userId()),
                orderRepository.findSnapshotsByUser(principal.shopId(), principal.userId(), offset, normalizedSize).stream()
                        .map(OrderResponseMapper::toResponse)
                        .toList()
        );
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
}
