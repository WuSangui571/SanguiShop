package com.sangui.shop.payment.client;

public interface ProductInventoryClient {

    void confirmReservation(Long shopId, String reservationNo, String traceId);
}
