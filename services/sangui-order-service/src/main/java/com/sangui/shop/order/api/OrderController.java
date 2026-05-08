package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.ConfirmOrderReceiptRequest;
import com.sangui.shop.order.api.dto.CreateOrderReviewRequest;
import com.sangui.shop.order.api.dto.CreateOrderRequest;
import com.sangui.shop.order.api.dto.OrderPageResponse;
import com.sangui.shop.order.api.dto.OrderReviewResponse;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.application.OrderCancelService;
import com.sangui.shop.order.application.OrderCreateService;
import com.sangui.shop.order.application.OrderQueryService;
import com.sangui.shop.order.application.OrderReceiptConfirmationService;
import com.sangui.shop.order.application.OrderReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderCreateService orderCreateService;
    private final OrderCancelService orderCancelService;
    private final OrderQueryService orderQueryService;
    private final OrderReceiptConfirmationService orderReceiptConfirmationService;
    private final OrderReviewService orderReviewService;

    public OrderController(
            OrderCreateService orderCreateService,
            OrderCancelService orderCancelService,
            OrderQueryService orderQueryService,
            OrderReceiptConfirmationService orderReceiptConfirmationService,
            OrderReviewService orderReviewService
    ) {
        this.orderCreateService = orderCreateService;
        this.orderCancelService = orderCancelService;
        this.orderQueryService = orderQueryService;
        this.orderReceiptConfirmationService = orderReceiptConfirmationService;
        this.orderReviewService = orderReviewService;
    }

    @PostMapping("/{orderId}/reviews")
    public ApiResult<OrderReviewResponse> createReview(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody CreateOrderReviewRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderReviewResponse response = orderReviewService.createReview(principal, orderId, request, traceId);
        return ApiResult.ok("ORDER_REVIEW_CREATED", response, traceId);
    }

    @GetMapping("/{orderId}/review")
    public ApiResult<OrderReviewResponse> getReview(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderReviewResponse response = orderReviewService.getReview(principal, orderId);
        return ApiResult.ok("ORDER_REVIEW_DETAIL", response, traceId);
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

    @PostMapping("/{orderId}/cancel")
    public ApiResult<OrderResponse> cancelOrder(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderResponse response = orderCancelService.cancelOrder(principal, orderId, traceId);
        return ApiResult.ok("ORDER_CANCELLED", response, traceId);
    }

    @PostMapping("/{orderId}/receipt-confirmations")
    public ApiResult<OrderResponse> confirmReceipt(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody ConfirmOrderReceiptRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderResponse response = orderReceiptConfirmationService.confirmReceipt(principal, orderId, request, traceId);
        return ApiResult.ok("ORDER_RECEIPT_CONFIRMED", response, traceId);
    }

    @GetMapping("/{orderId}")
    public ApiResult<OrderResponse> getOrder(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderResponse response = orderQueryService.getOrder(principal, orderId);
        return ApiResult.ok("ORDER_DETAIL", response, traceId);
    }

    @GetMapping
    public ApiResult<OrderPageResponse> listOrders(
            SanguiPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        OrderPageResponse response = orderQueryService.listOrders(principal, page, size);
        return ApiResult.ok("ORDER_LIST", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
