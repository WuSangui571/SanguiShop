package com.sangui.shop.seckill.api.dto;

import java.util.List;

public record AdminSeckillActivityPageResponse(
        int page,
        int size,
        int total,
        List<AdminSeckillActivitySummaryResponse> items
) {
}
