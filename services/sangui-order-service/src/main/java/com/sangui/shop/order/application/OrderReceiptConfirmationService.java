package com.sangui.shop.order.application;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.ConfirmOrderReceiptRequest;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReceiptConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(OrderReceiptConfirmationService.class);

    private final OrderRepository orderRepository;

    public OrderReceiptConfirmationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse confirmReceipt(
            SanguiPrincipal principal,
            Long orderId,
            ConfirmOrderReceiptRequest request,
            String traceId
    ) {
        String requestId = requireText(request.requestId());
        OrderSnapshot snapshot = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
        OrderRecord order = snapshot.order();
        if (order.status() == OrderStatus.COMPLETED) {
            logReceiptConfirmation(principal, order, requestId, traceId, "idempotent");
            return OrderResponseMapper.toResponse(snapshot);
        }
        if (order.status() != OrderStatus.SHIPPED) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }

        int updated = orderRepository.markCompleted(
                principal.shopId(),
                orderId,
                requestId,
                normalizeTraceId(traceId),
                LocalDateTime.now()
        );
        if (updated == 0) {
            OrderSnapshot latest = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
            if (latest.order().status() == OrderStatus.COMPLETED) {
                logReceiptConfirmation(principal, latest.order(), requestId, traceId, "idempotent");
                return OrderResponseMapper.toResponse(latest);
            }
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }

        OrderSnapshot completed = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
        logReceiptConfirmation(principal, completed.order(), requestId, traceId, "success");
        return OrderResponseMapper.toResponse(completed);
    }

    private OrderSnapshot requireOwnedOrder(Long shopId, String userId, Long orderId) {
        OrderSnapshot snapshot = orderRepository.findSnapshotById(shopId, orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (!Objects.equals(snapshot.order().userId(), userId)) {
            throw new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404);
        }
        return snapshot;
    }

    private void logReceiptConfirmation(
            SanguiPrincipal principal,
            OrderRecord order,
            String requestId,
            String traceId,
            String outcome
    ) {
        log.info(
                "Order receipt confirmation. traceId={} shopId={} userId={} orderId={} orderNo={} requestId={} outcome={}",
                normalizeTraceId(traceId),
                principal.shopId(),
                principal.userId(),
                order.id(),
                order.orderNo(),
                requestId,
                outcome
        );
    }

    private String requireText(String value) {
        String normalized = normalizeTraceId(value);
        if (normalized == null) {
            throw new SanguiException(com.sangui.shop.common.core.error.CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return normalized;
    }

    private String normalizeTraceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
