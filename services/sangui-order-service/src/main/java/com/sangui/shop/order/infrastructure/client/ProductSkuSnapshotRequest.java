package com.sangui.shop.order.infrastructure.client;

import java.util.List;

public record ProductSkuSnapshotRequest(
        Long shopId,
        List<Long> skuIds
) {
}
