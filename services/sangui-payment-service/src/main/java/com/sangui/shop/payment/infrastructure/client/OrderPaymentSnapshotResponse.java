package com.sangui.shop.payment.infrastructure.client;

public record OrderPaymentSnapshotResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String status,
        Long totalAmountCent
) {
}
