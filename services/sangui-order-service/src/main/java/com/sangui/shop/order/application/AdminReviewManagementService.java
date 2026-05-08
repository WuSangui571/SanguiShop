package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.AdminReviewPageResponse;
import com.sangui.shop.order.api.dto.AdminReviewSummaryResponse;
import com.sangui.shop.order.api.dto.AdminReviewVisibilityRequest;
import com.sangui.shop.order.domain.AdminReviewListItem;
import com.sangui.shop.order.domain.AdminReviewQuery;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.ReviewVisibilityStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReviewManagementService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderRepository orderRepository;

    public AdminReviewManagementService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public AdminReviewPageResponse listReviews(
            SanguiPrincipal principal,
            int page,
            int size,
            Long productId,
            Integer rating,
            String userId,
            String visibility,
            LocalDateTime fromTime,
            LocalDateTime toTime
    ) {
        requireAdmin(principal);
        validateFilters(productId, rating, fromTime, toTime);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        AdminReviewQuery query = new AdminReviewQuery(
                principal.shopId(),
                productId,
                rating,
                trimToNull(userId),
                parseOptionalVisibility(visibility),
                fromTime,
                toTime
        );
        int offset = (normalizedPage - 1) * normalizedSize;
        return new AdminReviewPageResponse(
                normalizedPage,
                normalizedSize,
                orderRepository.countAdminReviews(query),
                orderRepository.findAdminReviews(query, offset, normalizedSize)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @Transactional
    public AdminReviewSummaryResponse updateVisibility(
            SanguiPrincipal principal,
            Long reviewId,
            AdminReviewVisibilityRequest request,
            String traceId
    ) {
        requireAdmin(principal);
        if (reviewId == null || reviewId <= 0 || request == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        ReviewVisibilityStatus targetStatus = parseRequiredVisibility(request.visibility());
        String requestId = trimToNull(request.requestId());
        if (requestId == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        AdminReviewListItem existing = orderRepository.findAdminReviewById(principal.shopId(), reviewId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_REVIEW_NOT_FOUND, 404));
        if (requestId.equals(existing.visibilityRequestId())) {
            if (existing.visibilityStatus() != targetStatus) {
                throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
            }
            return toResponse(existing);
        }
        String reason = trimToNull(request.reason());
        orderRepository.updateReviewVisibility(
                principal.shopId(),
                reviewId,
                targetStatus,
                reason,
                requestId,
                principal.userId(),
                trimToNull(traceId),
                LocalDateTime.now()
        );
        return orderRepository.findAdminReviewById(principal.shopId(), reviewId)
                .map(this::toResponse)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_REVIEW_NOT_FOUND, 404));
    }

    private void requireAdmin(SanguiPrincipal principal) {
        boolean hasAdminRole = principal.roles() != null && principal.roles().contains(ADMIN_ROLE);
        boolean hasReviewAdminPermission = principal.permissions() != null
                && principal.permissions().contains(SanguiPermissionConstants.REVIEW_MANAGEMENT_ADMIN);
        if (!hasAdminRole && !hasReviewAdminPermission) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
    }

    private void validateFilters(Long productId, Integer rating, LocalDateTime fromTime, LocalDateTime toTime) {
        if (productId != null && productId <= 0) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private ReviewVisibilityStatus parseOptionalVisibility(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        return parseRequiredVisibility(normalized);
    }

    private ReviewVisibilityStatus parseRequiredVisibility(String value) {
        try {
            return ReviewVisibilityStatus.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private AdminReviewSummaryResponse toResponse(AdminReviewListItem item) {
        return new AdminReviewSummaryResponse(
                item.reviewId(),
                item.orderId(),
                item.orderNo(),
                item.productId(),
                item.skuId(),
                item.skuName(),
                item.rating(),
                item.content(),
                item.imageUrls() == null ? 0 : item.imageUrls().size(),
                maskUserId(item.userId()),
                item.visibilityStatus().value(),
                item.visibilityReason(),
                item.visibilityRequestId(),
                item.visibilityOperator(),
                item.visibilityTraceId(),
                toOffsetDateTime(item.visibilityUpdatedAt()),
                toOffsetDateTime(item.createdAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.of(value, RESPONSE_ZONE.getRules().getOffset(value));
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "***";
        }
        String value = userId.trim();
        if (value.length() <= 4) {
            return value.charAt(0) + "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
