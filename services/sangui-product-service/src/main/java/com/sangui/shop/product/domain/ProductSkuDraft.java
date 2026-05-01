package com.sangui.shop.product.domain;

public record ProductSkuDraft(
        String skuCode,
        String skuName,
        Long priceCent
) {
}
