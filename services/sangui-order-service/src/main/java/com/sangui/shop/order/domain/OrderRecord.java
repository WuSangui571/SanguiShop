package com.sangui.shop.order.domain;

public record OrderRecord(
        Long id,
        Long shopId,
        String userId,
        String orderNo,
        String requestId,
        OrderStatus status,
        Long totalAmountCent,
        String traceId
) {
}
