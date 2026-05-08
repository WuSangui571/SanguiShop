package com.sangui.shop.order.api.dto;

import java.util.List;

public record AdminReviewPageResponse(
        int page,
        int size,
        long total,
        List<AdminReviewSummaryResponse> items
) {
}
