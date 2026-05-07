package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.AdminCancelOrderRequest;
import com.sangui.shop.order.api.dto.AdminOrderDetailResponse;
import com.sangui.shop.order.api.dto.AdminOrderPageResponse;
import com.sangui.shop.order.application.AdminOrderManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
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
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderManagementService adminOrderManagementService;

    public AdminOrderController(AdminOrderManagementService adminOrderManagementService) {
        this.adminOrderManagementService = adminOrderManagementService;
    }

    @GetMapping
    public ApiResult<AdminOrderPageResponse> listOrders(
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
        AdminOrderPageResponse response = adminOrderManagementService.listOrders(
                principal,
                page,
                size,
                status,
                orderNo,
                userId,
                toLocalDateTime(fromTime),
                toLocalDateTime(toTime)
        );
        return ApiResult.ok("ADMIN_ORDER_LIST", response, traceId);
    }

    @GetMapping("/{orderId}")
    public ApiResult<AdminOrderDetailResponse> getOrder(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminOrderDetailResponse response = adminOrderManagementService.getOrder(principal, orderId);
        return ApiResult.ok("ADMIN_ORDER_DETAIL", response, traceId);
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResult<AdminOrderDetailResponse> cancelOrder(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long orderId,
            @Valid @RequestBody AdminCancelOrderRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminOrderDetailResponse response = adminOrderManagementService.cancelOrder(principal, orderId, request, traceId);
        return ApiResult.ok("ADMIN_ORDER_CANCELLED", response, traceId);
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toLocalDateTime();
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
