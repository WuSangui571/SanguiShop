package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.CreateOrderRequest;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.application.OrderCreateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderCreateService orderCreateService;

    public OrderController(OrderCreateService orderCreateService) {
        this.orderCreateService = orderCreateService;
    }

    @PostMapping
    public ApiResult<OrderResponse> createOrder(
            SanguiPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderResponse response = orderCreateService.createOrder(principal, request, traceId);
        return ApiResult.ok("ORDER_CREATED", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
