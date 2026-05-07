package com.sangui.shop.order.application;

import com.sangui.shop.order.api.dto.OrderItemResponse;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderSnapshot;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

final class OrderResponseMapper {

    private OrderResponseMapper() {
    }

    static OrderResponse toResponse(OrderSnapshot snapshot) {
        List<OrderItemResponse> items = snapshot.items().stream()
                .map(OrderResponseMapper::toItemResponse)
                .toList();
        return new OrderResponse(
                snapshot.order().id(),
                snapshot.order().orderNo(),
                snapshot.order().shopId(),
                snapshot.order().userId(),
                snapshot.order().requestId(),
                snapshot.order().status().value(),
                snapshot.order().totalAmountCent(),
                items,
                toOffsetDateTime(snapshot.order().createdAt()),
                toOffsetDateTime(snapshot.order().updatedAt()),
                fulfillmentStatus(snapshot.order().status().value(), snapshot.order().fulfillmentStatus()),
                snapshot.order().carrier(),
                snapshot.order().trackingNo(),
                toOffsetDateTime(snapshot.order().shippedAt())
        );
    }

    private static String fulfillmentStatus(String orderStatus, String persistedStatus) {
        if ("shipped".equals(orderStatus)) {
            return "shipped";
        }
        if ("paid".equals(orderStatus)) {
            return "unshipped";
        }
        return persistedStatus == null ? "pending" : persistedStatus;
    }

    private static OrderItemResponse toItemResponse(OrderItemRecord item) {
        return new OrderItemResponse(
                item.productId(),
                item.skuId(),
                item.skuName(),
                item.priceCent(),
                item.quantity(),
                item.lineAmountCent()
        );
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
