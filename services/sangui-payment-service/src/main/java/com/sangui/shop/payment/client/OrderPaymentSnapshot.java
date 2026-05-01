package com.sangui.shop.payment.client;

public record OrderPaymentSnapshot(
        Long orderId,
        String orderNo,
        Long shopId,
        String userId,
        String status,
        Long totalAmountCent
) {
}
