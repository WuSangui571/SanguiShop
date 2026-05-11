package com.sangui.shop.seckill.infrastructure;

import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UnavailableProductSkuSnapshotClient implements ProductSkuSnapshotClient {

    @Override
    public Optional<ProductSkuSnapshot> findBySkuId(Long shopId, Long skuId) {
        return Optional.empty();
    }
}
