package com.sangui.shop.order.client.dto;

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
