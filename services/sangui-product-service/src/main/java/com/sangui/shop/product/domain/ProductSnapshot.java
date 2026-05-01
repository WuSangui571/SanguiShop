package com.sangui.shop.product.domain;

import java.util.List;

public record ProductSnapshot(
        ProductRecord product,
        List<ProductSkuRecord> skus
) {
}
