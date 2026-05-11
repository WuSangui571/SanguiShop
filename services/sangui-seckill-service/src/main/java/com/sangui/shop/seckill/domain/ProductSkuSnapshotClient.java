package com.sangui.shop.seckill.domain;

import java.util.Optional;

public interface ProductSkuSnapshotClient {

    Optional<ProductSkuSnapshot> findBySkuId(Long shopId, Long skuId);

    record ProductSkuSnapshot(
            Long productId,
            String productName,
            Long skuId,
            String skuCode,
            String skuName,
            Long priceCent,
            Long availableStock
    ) {
    }
}
