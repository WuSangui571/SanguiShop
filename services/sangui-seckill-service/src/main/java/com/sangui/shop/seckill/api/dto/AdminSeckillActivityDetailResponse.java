package com.sangui.shop.seckill.api.dto;

import com.sangui.shop.seckill.domain.SeckillActivity;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminSeckillActivityDetailResponse(
        Long activityId,
        String activityName,
        String description,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime serverTime,
        int skuCount,
        long totalActivityStock,
        long soldCount,
        List<AdminSeckillActivitySkuResponse> skus
) {
    public static AdminSeckillActivityDetailResponse from(SeckillActivity activity, OffsetDateTime serverTime) {
        return new AdminSeckillActivityDetailResponse(
                activity.id(),
                activity.activityName(),
                activity.description(),
                activity.status().value(),
                toOffsetDateTime(activity.startsAt()),
                toOffsetDateTime(activity.endsAt()),
                serverTime,
                activity.skuCount(),
                activity.totalActivityStock(),
                activity.soldCount(),
                activity.skus() == null ? List.of() : activity.skus().stream()
                        .map(AdminSeckillActivitySkuResponse::from)
                        .toList()
        );
    }

    private static OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
    }
}
