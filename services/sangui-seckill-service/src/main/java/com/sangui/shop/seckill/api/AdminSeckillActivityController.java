package com.sangui.shop.seckill.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityBindSkuRequest;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityDetailResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityDraftRequest;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityPageResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityStatusUpdateRequest;
import com.sangui.shop.seckill.application.AdminSeckillActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/seckill/activities")
public class AdminSeckillActivityController {

    private final AdminSeckillActivityService adminSeckillActivityService;

    public AdminSeckillActivityController(AdminSeckillActivityService adminSeckillActivityService) {
        this.adminSeckillActivityService = adminSeckillActivityService;
    }

    @GetMapping
    public ApiResult<AdminSeckillActivityPageResponse> listActivities(
            SanguiPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminSeckillActivityPageResponse response = adminSeckillActivityService.listActivities(principal, page, size, status);
        return ApiResult.ok("ADMIN_SECKILL_ACTIVITY_LIST", response, traceId);
    }

    @GetMapping("/{activityId}")
    public ApiResult<AdminSeckillActivityDetailResponse> getActivity(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long activityId,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminSeckillActivityDetailResponse response = adminSeckillActivityService.getActivity(principal, activityId);
        return ApiResult.ok("ADMIN_SECKILL_ACTIVITY_DETAIL", response, traceId);
    }

    @PostMapping
    public ApiResult<AdminSeckillActivityDetailResponse> createActivity(
            SanguiPrincipal principal,
            @Valid @RequestBody AdminSeckillActivityDraftRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminSeckillActivityDetailResponse response = adminSeckillActivityService.createActivity(principal, request, traceId);
        return ApiResult.ok("ADMIN_SECKILL_ACTIVITY_CREATED", response, traceId);
    }

    @PutMapping("/{activityId}")
    public ApiResult<AdminSeckillActivityDetailResponse> updateActivity(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long activityId,
            @Valid @RequestBody AdminSeckillActivityDraftRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminSeckillActivityDetailResponse response = adminSeckillActivityService.updateActivity(principal, activityId, request, traceId);
        return ApiResult.ok("ADMIN_SECKILL_ACTIVITY_UPDATED", response, traceId);
    }

    @PostMapping("/{activityId}/status")
    public ApiResult<AdminSeckillActivityDetailResponse> updateStatus(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long activityId,
            @Valid @RequestBody AdminSeckillActivityStatusUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminSeckillActivityDetailResponse response = adminSeckillActivityService.updateStatus(principal, activityId, request, traceId);
        return ApiResult.ok("ADMIN_SECKILL_ACTIVITY_STATUS_UPDATED", response, traceId);
    }

    @PostMapping("/{activityId}/skus")
    public ApiResult<AdminSeckillActivityDetailResponse> bindSku(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long activityId,
            @Valid @RequestBody AdminSeckillActivityBindSkuRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminSeckillActivityDetailResponse response = adminSeckillActivityService.bindSku(principal, activityId, request, traceId);
        return ApiResult.ok("ADMIN_SECKILL_ACTIVITY_SKU_BOUND", response, traceId);
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
