package com.sangui.shop.seckill.domain;

import java.time.LocalDateTime;
import java.util.List;

public record SeckillActivity(
        Long id,
        Long shopId,
        String activityName,
        String description,
        SeckillActivityStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String requestId,
        String traceId,
        List<SeckillActivitySku> skus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public int skuCount() {
        return skus == null ? 0 : skus.size();
    }

    public long totalActivityStock() {
        if (skus == null) {
            return 0;
        }
        return skus.stream().mapToLong(SeckillActivitySku::activityStock).sum();
    }

    public long soldCount() {
        if (skus == null) {
            return 0;
        }
        return skus.stream().mapToLong(SeckillActivitySku::soldCount).sum();
    }

    public SeckillActivity withStatus(SeckillActivityStatus newStatus) {
        return new SeckillActivity(
                id, shopId, activityName, description, newStatus,
                startsAt, endsAt, requestId, traceId, skus,
                createdAt, updatedAt
        );
    }

    public SeckillActivity withSkuBound(List<SeckillActivitySku> updatedSkus) {
        return new SeckillActivity(
                id, shopId, activityName, description, status,
                startsAt, endsAt, requestId, traceId, updatedSkus,
                createdAt, updatedAt
        );
    }

    public SeckillActivity withIdAndTimestamps(Long newId, String newTraceId, LocalDateTime now) {
        return new SeckillActivity(
                newId, shopId, activityName, description, status,
                startsAt, endsAt, requestId, newTraceId, skus,
                now, now
        );
    }
}
