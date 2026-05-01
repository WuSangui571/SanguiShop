package com.sangui.shop.product.domain;

import com.sangui.shop.common.core.api.PageRequest;
import com.sangui.shop.common.core.api.PageResponse;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    PageResponse<ProductListItem> listActiveProducts(Long shopId, PageRequest pageRequest);

    Optional<ProductSnapshot> findPublicProduct(Long shopId, Long productId);

    Optional<ProductSnapshot> findAdminProduct(Long shopId, Long productId);

    List<ProductSkuRecord> findActiveSkus(Long shopId, List<Long> skuIds);

    List<ProductInventoryReservationRecord> findReservationRecords(Long shopId, String reservationNo);

    int reserveSkuStock(Long shopId, Long skuId, int quantity);

    int confirmReservedSkuStock(Long shopId, Long skuId, int quantity);

    int releaseReservedSkuStock(Long shopId, Long skuId, int quantity);

    int updateReservationStatus(
            Long shopId,
            String reservationNo,
            ProductInventoryReservationStatus currentStatus,
            ProductInventoryReservationStatus nextStatus,
            String traceId
    );

    void createReservationRecords(
            Long shopId,
            String reservationNo,
            List<ProductInventoryReservationRecord> records,
            ProductInventoryReservationStatus status,
            String traceId
    );

    Long createProduct(Long shopId, String operatorUserId, ProductDraft draft, ProductStatus status);

    void updateProduct(Long shopId, Long productId, String operatorUserId, ProductDraft draft);

    void updateProductStatus(Long shopId, Long productId, String operatorUserId, ProductStatus status);
}
