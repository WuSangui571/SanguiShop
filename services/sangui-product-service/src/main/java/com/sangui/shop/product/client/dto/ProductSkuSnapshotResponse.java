package com.sangui.shop.product.client.dto;

import java.util.List;

public record ProductSkuSnapshotResponse(
        List<ProductSkuSnapshotItemResponse> items
) {
}
