package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.AdminReviewPageResponse;
import com.sangui.shop.order.api.dto.AdminReviewSummaryResponse;
import com.sangui.shop.order.api.dto.AdminReviewVisibilityRequest;
import com.sangui.shop.order.application.AdminReviewManagementService;
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
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final AdminReviewManagementService adminReviewManagementService;

    public AdminReviewController(AdminReviewManagementService adminReviewManagementService) {
        this.adminReviewManagementService = adminReviewManagementService;
    }

    @GetMapping
    public ApiResult<AdminReviewPageResponse> listReviews(
            SanguiPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toTime,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminReviewPageResponse response = adminReviewManagementService.listReviews(
                principal,
                page,
                size,
                productId,
                rating,
                userId,
                visibility,
                toLocalDateTime(fromTime),
                toLocalDateTime(toTime)
        );
        return ApiResult.ok("ADMIN_REVIEW_LIST", response, traceId);
    }

    @PostMapping("/{reviewId}/visibility")
    public ApiResult<AdminReviewSummaryResponse> updateVisibility(
            SanguiPrincipal principal,
            @PathVariable @Min(1) Long reviewId,
            @Valid @RequestBody AdminReviewVisibilityRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        AdminReviewSummaryResponse response = adminReviewManagementService.updateVisibility(principal, reviewId, request, traceId);
        return ApiResult.ok("ADMIN_REVIEW_VISIBILITY_UPDATED", response, traceId);
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
