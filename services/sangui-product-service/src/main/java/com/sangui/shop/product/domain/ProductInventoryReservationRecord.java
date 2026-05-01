package com.sangui.shop.product.domain;

public record ProductInventoryReservationRecord(
        Long shopId,
        String reservationNo,
        Long productId,
        Long skuId,
        String skuCode,
        String skuName,
        Long priceCent,
        int quantity,
        ProductInventoryReservationStatus status,
        String traceId
) {
    public ProductInventoryReservationRecord withStatus(ProductInventoryReservationStatus nextStatus, String nextTraceId) {
        return new ProductInventoryReservationRecord(
                shopId,
                reservationNo,
                productId,
                skuId,
                skuCode,
                skuName,
                priceCent,
                quantity,
                nextStatus,
                nextTraceId == null ? traceId : nextTraceId
        );
    }
}
