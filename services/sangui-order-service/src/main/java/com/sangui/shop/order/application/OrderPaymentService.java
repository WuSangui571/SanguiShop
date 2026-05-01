package com.sangui.shop.order.application;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.dto.ConfirmOrderPaymentRequest;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotRequest;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotResponse;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;

    public OrderPaymentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderPaymentSnapshotResponse getPayableOrder(OrderPaymentSnapshotRequest request) {
        OrderRecord order = requireOwnedOrder(request.shopId(), request.userId(), request.orderId());
        if (order.status() != OrderStatus.CREATED) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }
        return toResponse(order);
    }

    @Transactional
    public OrderPaymentSnapshotResponse confirmPaid(ConfirmOrderPaymentRequest request) {
        OrderRecord order = requireOwnedOrder(request.shopId(), request.userId(), request.orderId());
        verifyAmount(order, request.paidAmountCent());

        if (order.status() == OrderStatus.PAID) {
            return toResponse(order);
        }
        if (order.status() != OrderStatus.CREATED) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }

        int updated = orderRepository.updateStatus(order.shopId(), order.id(), OrderStatus.CREATED, OrderStatus.PAID);
        if (updated == 0) {
            OrderRecord latest = requireOwnedOrder(request.shopId(), request.userId(), request.orderId());
            verifyAmount(latest, request.paidAmountCent());
            if (latest.status() == OrderStatus.PAID) {
                return toResponse(latest);
            }
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }

        OrderRecord paidOrder = requireOwnedOrder(request.shopId(), request.userId(), request.orderId());
        return toResponse(paidOrder);
    }

    private OrderRecord requireOwnedOrder(Long shopId, String userId, Long orderId) {
        OrderRecord order = orderRepository.findById(shopId, orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (!Objects.equals(order.userId(), userId)) {
            throw new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404);
        }
        return order;
    }

    private void verifyAmount(OrderRecord order, Long paidAmountCent) {
        if (!Objects.equals(order.totalAmountCent(), paidAmountCent)) {
            throw new SanguiException(OrderErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH, 409);
        }
    }

    private OrderPaymentSnapshotResponse toResponse(OrderRecord order) {
        return new OrderPaymentSnapshotResponse(
                order.id(),
                order.orderNo(),
                order.shopId(),
                order.userId(),
                order.reservationNo(),
                order.status().value(),
                order.totalAmountCent()
        );
    }
}
