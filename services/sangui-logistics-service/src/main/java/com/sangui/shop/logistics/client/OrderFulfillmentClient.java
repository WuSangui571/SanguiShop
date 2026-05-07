package com.sangui.shop.logistics.client;

public interface OrderFulfillmentClient {

    FulfillmentOrderPageResponse queryFulfillments(FulfillmentOrderQueryRequest request, String traceId);

    FulfillmentOrderResponse getFulfillment(FulfillmentOrderDetailRequest request, String traceId);

    FulfillmentOrderResponse confirmShipment(ConfirmOrderShipmentRequest request, String traceId);
}
