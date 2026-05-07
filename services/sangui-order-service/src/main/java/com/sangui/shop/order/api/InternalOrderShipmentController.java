package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.order.application.OrderShipmentService;
import com.sangui.shop.order.client.dto.ConfirmOrderShipmentRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderDetailRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderPageResponse;
import com.sangui.shop.order.client.dto.FulfillmentOrderQueryRequest;
import com.sangui.shop.order.client.dto.FulfillmentOrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderShipmentController {

    private final OrderShipmentService orderShipmentService;

    public InternalOrderShipmentController(OrderShipmentService orderShipmentService) {
        this.orderShipmentService = orderShipmentService;
    }

    @PostMapping("/fulfillment-records/query")
    public ApiResult<FulfillmentOrderPageResponse> queryFulfillmentRecords(
            @Valid @RequestBody FulfillmentOrderQueryRequest request,
            HttpServletRequest httpRequest
    ) {
        FulfillmentOrderPageResponse response = orderShipmentService.queryFulfillmentRecords(request);
        return ApiResult.ok("ORDER_FULFILLMENT_RECORDS_FETCHED", response, traceId(httpRequest));
    }

    @PostMapping("/fulfillment-records/detail")
    public ApiResult<FulfillmentOrderResponse> getFulfillmentRecord(
            @Valid @RequestBody FulfillmentOrderDetailRequest request,
            HttpServletRequest httpRequest
    ) {
        FulfillmentOrderResponse response = orderShipmentService.getFulfillmentRecord(request);
        return ApiResult.ok("ORDER_FULFILLMENT_RECORD_FETCHED", response, traceId(httpRequest));
    }

    @PostMapping("/shipments/confirmations")
    public ApiResult<FulfillmentOrderResponse> confirmShipment(
            @Valid @RequestBody ConfirmOrderShipmentRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        FulfillmentOrderResponse response = orderShipmentService.confirmShipment(request, traceId);
        return ApiResult.ok("ORDER_SHIPPED", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
