package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.order.application.OrderPaymentService;
import com.sangui.shop.order.client.dto.ConfirmOrderPaymentRequest;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotRequest;
import com.sangui.shop.order.client.dto.OrderPaymentSnapshotResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderPaymentController {

    private final OrderPaymentService orderPaymentService;

    public InternalOrderPaymentController(OrderPaymentService orderPaymentService) {
        this.orderPaymentService = orderPaymentService;
    }

    @PostMapping("/payment-snapshot")
    public ApiResult<OrderPaymentSnapshotResponse> getPayableOrder(
            @Valid @RequestBody OrderPaymentSnapshotRequest request,
            HttpServletRequest httpRequest
    ) {
        OrderPaymentSnapshotResponse response = orderPaymentService.getPayableOrder(request);
        return ApiResult.ok("ORDER_PAYMENT_SNAPSHOT_FETCHED", response, traceId(httpRequest));
    }

    @PostMapping("/payment-confirmations")
    public ApiResult<OrderPaymentSnapshotResponse> confirmPaid(
            @Valid @RequestBody ConfirmOrderPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        OrderPaymentSnapshotResponse response = orderPaymentService.confirmPaid(request);
        return ApiResult.ok("ORDER_PAID", response, traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
