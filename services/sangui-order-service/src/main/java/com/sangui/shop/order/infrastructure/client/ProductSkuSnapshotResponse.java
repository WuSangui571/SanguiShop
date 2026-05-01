package com.sangui.shop.order.infrastructure.client;

import java.util.List;

public record ProductSkuSnapshotResponse(
        List<ProductSkuSnapshotItemResponse> items
) {
}
