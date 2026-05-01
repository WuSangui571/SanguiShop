package com.sangui.shop.order.domain;

public record OrderItemDraft(
        Long productId,
        Long skuId,
        String skuName,
        Long priceCent,
        Integer quantity
) {
    public long lineAmountCent() {
        return priceCent * quantity.longValue();
    }
}
