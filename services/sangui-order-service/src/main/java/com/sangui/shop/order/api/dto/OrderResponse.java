package com.sangui.shop.order.api.dto;

import java.util.List;

public record OrderResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String requestId,
        String status,
        Long totalAmountCent,
        List<OrderItemResponse> items
) {
}
