package com.sangui.shop.seckill.api.dto;

import com.sangui.shop.seckill.domain.SeckillActivity;
import java.time.OffsetDateTime;

public record AdminSeckillActivitySummaryResponse(
        Long activityId,
        String activityName,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime serverTime,
        int skuCount,
        long totalActivityStock,
        long soldCount
) {
    public static AdminSeckillActivitySummaryResponse from(SeckillActivity activity, OffsetDateTime serverTime) {
        return new AdminSeckillActivitySummaryResponse(
                activity.id(),
                activity.activityName(),
                activity.status().value(),
                toOffsetDateTime(activity.startsAt()),
                toOffsetDateTime(activity.endsAt()),
                serverTime,
                activity.skuCount(),
                activity.totalActivityStock(),
                activity.soldCount()
        );
    }

    private static OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
    }
}
