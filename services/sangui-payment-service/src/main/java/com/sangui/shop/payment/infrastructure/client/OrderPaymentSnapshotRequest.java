package com.sangui.shop.payment.infrastructure.client;

public record OrderPaymentSnapshotRequest(
        Long shopId,
        String userId,
        Long orderId
) {
}
