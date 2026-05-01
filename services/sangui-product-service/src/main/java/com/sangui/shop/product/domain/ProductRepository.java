package com.sangui.shop.product.domain;

import com.sangui.shop.common.core.api.PageRequest;
import com.sangui.shop.common.core.api.PageResponse;
import java.util.Optional;

public interface ProductRepository {

    PageResponse<ProductListItem> listActiveProducts(Long shopId, PageRequest pageRequest);

    Optional<ProductSnapshot> findPublicProduct(Long shopId, Long productId);

    Optional<ProductSnapshot> findAdminProduct(Long shopId, Long productId);

    Long createProduct(Long shopId, String operatorUserId, ProductDraft draft, ProductStatus status);

    void updateProduct(Long shopId, Long productId, String operatorUserId, ProductDraft draft);

    void updateProductStatus(Long shopId, Long productId, String operatorUserId, ProductStatus status);
}
