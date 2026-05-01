package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersRequest;
import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import com.sangui.shop.order.application.OrderTimeoutCancelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderTimeoutController {

    private final OrderTimeoutCancelService orderTimeoutCancelService;

    public InternalOrderTimeoutController(OrderTimeoutCancelService orderTimeoutCancelService) {
        this.orderTimeoutCancelService = orderTimeoutCancelService;
    }

    @PostMapping("/timeout-cancellations")
    public ApiResult<CancelExpiredOrdersResponse> cancelExpiredOrders(
            @Valid @RequestBody CancelExpiredOrdersRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        CancelExpiredOrdersResponse response = orderTimeoutCancelService.cancelExpiredOrders(request, traceId);
        return ApiResult.ok("ORDER_TIMEOUT_CANCELLED", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
