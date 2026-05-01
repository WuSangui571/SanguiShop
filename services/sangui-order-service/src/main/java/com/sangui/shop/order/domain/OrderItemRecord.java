package com.sangui.shop.order.domain;

public record OrderItemRecord(
        Long id,
        Long orderId,
        Long productId,
        Long skuId,
        String skuName,
        Long priceCent,
        Integer quantity,
        Long lineAmountCent
) {
}
