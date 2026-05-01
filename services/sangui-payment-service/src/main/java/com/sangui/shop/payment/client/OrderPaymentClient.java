package com.sangui.shop.payment.client;

public interface OrderPaymentClient {

    OrderPaymentSnapshot getPayableOrder(Long shopId, String userId, Long orderId);

    OrderPaymentSnapshot confirmPaid(
            Long shopId,
            String userId,
            Long orderId,
            String paymentNo,
            Long paidAmountCent,
            String traceId
    );
}
