package com.sangui.shop.payment.infrastructure.client;

public record OrderPaymentSnapshotResponse(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String reservationNo,
        String status,
        Long totalAmountCent
) {
}
