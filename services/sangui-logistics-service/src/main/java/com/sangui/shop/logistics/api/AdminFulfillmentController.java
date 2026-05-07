package com.sangui.shop.logistics.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.logistics.api.dto.ShipFulfillmentRequest;
import com.sangui.shop.logistics.application.AdminFulfillmentService;
import com.sangui.shop.logistics.client.FulfillmentOrderPageResponse;
import com.sangui.shop.logistics.client.FulfillmentOrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/fulfillments")
public class AdminFulfillmentController {

    private final AdminFulfillmentService adminFulfillmentService;

    public AdminFulfillmentController(AdminFulfillmentService adminFulfillmentService) {
        this.adminFulfillmentService = adminFulfillmentService;
    }

    @GetMapping
    public ApiResult<FulfillmentOrderPageResponse> listFulfillments(
            SanguiPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        FulfillmentOrderPageResponse response = adminFulfillmentService.listFulfillments(
                principal,
                page,
                size,
                status,
                orderNo,
                userId,
                fromTime,
                toTime,
                traceId
        );
        return ApiResult.ok("ADMIN_FULFILLMENT_LIST", response, traceId);
    }

    @GetMapping("/{orderId}")
    public ApiResult<FulfillmentOrderResponse> getFulfillment(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        FulfillmentOrderResponse response = adminFulfillmentService.getFulfillment(principal, orderId, traceId);
        return ApiResult.ok("ADMIN_FULFILLMENT_DETAIL", response, traceId);
    }

    @PostMapping("/{orderId}/ship")
    public ApiResult<FulfillmentOrderResponse> shipFulfillment(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody ShipFulfillmentRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        FulfillmentOrderResponse response = adminFulfillmentService.shipFulfillment(principal, orderId, request, traceId);
        return ApiResult.ok("ADMIN_FULFILLMENT_SHIPPED", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
