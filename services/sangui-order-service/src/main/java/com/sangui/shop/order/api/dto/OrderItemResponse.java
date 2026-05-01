package com.sangui.shop.order.api.dto;

public record OrderItemResponse(
        Long productId,
        Long skuId,
        String skuName,
        Long priceCent,
        Integer quantity,
        Long lineAmountCent
) {
}
