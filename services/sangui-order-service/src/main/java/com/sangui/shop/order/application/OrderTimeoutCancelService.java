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
        int timeoutMinutes = request.timeoutMinutes() == null ? DEFAULT_TIMEOUT_MINUTES : request.timeoutMinutes();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
        List<OrderRecord> expiredOrders = orderRepository.findExpiredCreatedOrders(request.shopId(), cutoff, limit);

        int cancelledCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        for (OrderRecord order : expiredOrders) {
            try {
                if (cancelOne(order, traceId)) {
                    cancelledCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn(
                        "Order timeout compensation failed. traceId={} shopId={} orderId={} orderNo={} reservationNo={} errorType={} errorCode={} message={}",
                        normalizeTraceId(traceId),
                        order.shopId(),
                        order.id(),
                        order.orderNo(),
                        order.reservationNo(),
                        exception.getClass().getSimpleName(),
                        errorCode(exception),
                        sanitizeMessage(exception)
                );
            }
        }
        return new CancelExpiredOrdersResponse(request.shopId(), expiredOrders.size(), cancelledCount, skippedCount, failedCount);
    }

    private boolean cancelOne(OrderRecord order, String traceId) {
        OrderRecord latest = orderRepository.findById(order.shopId(), order.id())
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (latest.status() != OrderStatus.CREATED) {
            return false;
        }
        productCatalogClient.releaseInventory(latest.shopId(), latest.reservationNo(), normalizeTraceId(traceId));
        int updated = orderRepository.updateStatus(latest.shopId(), latest.id(), OrderStatus.CREATED, OrderStatus.CANCELLED);
        if (updated > 0) {
            return true;
        }
        OrderRecord refreshed = orderRepository.findById(order.shopId(), order.id())
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (refreshed.status() == OrderStatus.CANCELLED || refreshed.status() == OrderStatus.PAID) {
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

    private String errorCode(RuntimeException exception) {
        if (exception instanceof SanguiException sanguiException) {
            return sanguiException.errorCode().code();
        }
        return "INTERNAL_ERROR";
    }

    private String sanitizeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "";
        }
        return message.replaceAll("[\\r\\n]+", " ").trim();
    }
}
