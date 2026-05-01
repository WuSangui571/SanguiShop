package com.sangui.shop.product.domain;

import java.util.List;

public record ProductDraft(
        String productName,
        String productDescription,
        List<ProductSkuDraft> skus
) {
}
