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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTimeoutCancelService {

    private static final int DEFAULT_TIMEOUT_MINUTES = 15;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;
    private final Clock clock;

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
        int timeoutMinutes = request.timeoutMinutes() == null ? DEFAULT_TIMEOUT_MINUTES : request.timeoutMinutes();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
        List<OrderRecord> expiredOrders = orderRepository.findExpiredCreatedOrders(request.shopId(), cutoff, limit);

        int cancelledCount = 0;
        int skippedCount = 0;
        for (OrderRecord order : expiredOrders) {
            if (cancelOne(order, traceId)) {
                cancelledCount++;
            } else {
                skippedCount++;
            }
        }
        return new CancelExpiredOrdersResponse(request.shopId(), expiredOrders.size(), cancelledCount, skippedCount);
    }

    private boolean cancelOne(OrderRecord order, String traceId) {
        if (order.status() != OrderStatus.CREATED) {
            return false;
        }
        productCatalogClient.releaseInventory(order.shopId(), order.reservationNo(), normalizeTraceId(traceId));
        int updated = orderRepository.updateStatus(order.shopId(), order.id(), OrderStatus.CREATED, OrderStatus.CANCELLED);
        if (updated > 0) {
            return true;
        }
        OrderRecord latest = orderRepository.findById(order.shopId(), order.id())
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (latest.status() == OrderStatus.CANCELLED || latest.status() == OrderStatus.PAID) {
            return false;
        }
        throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String trimmed = traceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
