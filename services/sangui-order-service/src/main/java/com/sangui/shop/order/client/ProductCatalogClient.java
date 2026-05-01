package com.sangui.shop.order.client;

import java.util.List;

public interface ProductCatalogClient {

    List<ProductSkuSnapshot> listActiveSkuSnapshots(Long shopId, List<Long> skuIds);
}
