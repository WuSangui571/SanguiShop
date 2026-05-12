package com.sangui.shop.seckill.infrastructure;

import com.sangui.shop.seckill.domain.ActivityRepository;
import com.sangui.shop.seckill.domain.SeckillActivity;
import com.sangui.shop.seckill.domain.SeckillActivitySku;
import com.sangui.shop.seckill.domain.SeckillActivityStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
public class InMemoryActivityRepository implements ActivityRepository {

    private final AtomicLong nextActivityId = new AtomicLong(1000);
    private final AtomicLong nextSkuRowId = new AtomicLong(2000);
    private final Map<Long, SeckillActivity> activitiesById = new LinkedHashMap<>();
    private final Map<String, SeckillActivity> activitiesByRequest = new LinkedHashMap<>();
    private final Map<Long, List<SeckillActivitySku>> skusByActivityId = new LinkedHashMap<>();
    private final Map<String, SeckillActivitySku> skusByRequest = new LinkedHashMap<>();
    private final Map<String, StatusRequestRecord> statusRequests = new LinkedHashMap<>();

    @Override
    public synchronized Optional<SeckillActivity> findById(Long shopId, Long activityId) {
        SeckillActivity activity = activitiesById.get(activityId);
        if (activity == null || !activity.shopId().equals(shopId)) {
            return Optional.empty();
        }
        return Optional.of(activity.withSkuBound(List.copyOf(skusByActivityId.getOrDefault(activityId, List.of()))));
    }

    @Override
    public synchronized Optional<SeckillActivity> findByRequestId(Long shopId, String requestId) {
        SeckillActivity activity = activitiesByRequest.get(activityRequestKey(shopId, requestId));
        if (activity == null || !activity.shopId().equals(shopId)) {
            return Optional.empty();
        }
        return Optional.of(activity.withSkuBound(List.copyOf(skusByActivityId.getOrDefault(activity.id(), List.of()))));
    }

    @Override
    public synchronized List<SeckillActivity> findPage(Long shopId, String status, int offset, int size) {
        return activitiesById.values().stream()
                .filter(activity -> activity.shopId().equals(shopId))
                .filter(activity -> status == null || activity.status().value().equals(status))
                .skip(offset)
                .limit(size)
                .map(activity -> activity.withSkuBound(List.copyOf(skusByActivityId.getOrDefault(activity.id(), List.of()))))
                .toList();
    }

    @Override
    public synchronized int count(Long shopId, String status) {
        return (int) activitiesById.values().stream()
                .filter(activity -> activity.shopId().equals(shopId))
                .filter(activity -> status == null || activity.status().value().equals(status))
                .count();
    }

    @Override
    public synchronized Long create(SeckillActivity activity, List<SeckillActivitySku> skus) {
        Long activityId = activity.id();
        if (activityId == null || !activitiesById.containsKey(activityId)) {
            activityId = nextActivityId.incrementAndGet();
        }
        SeckillActivity stored = activity.withIdAndTimestamps(activityId, activity.traceId(), LocalDateTime.now());
        activitiesById.put(activityId, stored);
        activitiesByRequest.put(activityRequestKey(stored.shopId(), stored.requestId()), stored);
        if (skus != null) {
            List<SeckillActivitySku> storedSkus = new ArrayList<>();
            for (SeckillActivitySku sku : skus) {
                SeckillActivitySku storedSku = new SeckillActivitySku(
                        nextSkuRowId.incrementAndGet(),
                        activityId,
                        sku.productId(),
                        sku.productName(),
                        sku.skuId(),
                        sku.skuCode(),
                        sku.skuName(),
                        sku.priceCent(),
                        sku.seckillPriceCent(),
                        sku.availableStock(),
                        sku.activityStock(),
                        sku.soldCount(),
                        sku.requestId()
                );
                storedSkus.add(storedSku);
                if (storedSku.requestId() != null) {
                    skusByRequest.put(skuRequestKey(stored.shopId(), activityId, storedSku.requestId()), storedSku);
                }
            }
            skusByActivityId.put(activityId, storedSkus);
        }
        return activityId;
    }

    @Override
    public synchronized int updateActivityStatus(Long shopId, Long activityId, SeckillActivityStatus currentStatus,
                                                 SeckillActivityStatus newStatus) {
        SeckillActivity activity = activitiesById.get(activityId);
        if (activity == null || !activity.shopId().equals(shopId) || activity.status() != currentStatus) {
            return 0;
        }
        SeckillActivity updated = activity.withStatus(newStatus);
        activitiesById.put(activityId, updated);
        activitiesByRequest.put(activityRequestKey(updated.shopId(), updated.requestId()), updated);
        return 1;
    }

    @Override
    public synchronized int upsertSku(Long shopId, Long activityId, SeckillActivitySku sku) {
        SeckillActivity activity = activitiesById.get(activityId);
        if (activity == null || !activity.shopId().equals(shopId)) {
            return 0;
        }
        List<SeckillActivitySku> currentSkus = skusByActivityId.computeIfAbsent(activityId, ignored -> new ArrayList<>());
        SeckillActivitySku storedSku = new SeckillActivitySku(
                nextSkuRowId.incrementAndGet(),
                activityId,
                sku.productId(),
                sku.productName(),
                sku.skuId(),
                sku.skuCode(),
                sku.skuName(),
                sku.priceCent(),
                sku.seckillPriceCent(),
                sku.availableStock(),
                sku.activityStock(),
                sku.soldCount(),
                sku.requestId()
        );
        currentSkus.removeIf(existing -> existing.skuId().equals(storedSku.skuId()));
        currentSkus.add(storedSku);
        if (storedSku.requestId() != null) {
            skusByRequest.put(skuRequestKey(shopId, activityId, storedSku.requestId()), storedSku);
        }
        return 1;
    }

    @Override
    public synchronized Optional<SeckillActivitySku> findSkuByRequestId(Long shopId, Long activityId, String requestId) {
        SeckillActivity activity = activitiesById.get(activityId);
        if (activity == null || !activity.shopId().equals(shopId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(skusByRequest.get(skuRequestKey(shopId, activityId, requestId)));
    }

    @Override
    public synchronized Optional<StatusRequestRecord> findStatusRequestByRequestId(Long shopId, Long activityId, String requestId) {
        return Optional.ofNullable(statusRequests.get(statusRequestKey(shopId, activityId, requestId)));
    }

    @Override
    public synchronized void saveStatusRequest(Long shopId, Long activityId, String requestId, SeckillActivityStatus targetStatus, String traceId) {
        statusRequests.put(statusRequestKey(shopId, activityId, requestId),
                new StatusRequestRecord(shopId, activityId, requestId, targetStatus));
    }

    private String activityRequestKey(Long shopId, String requestId) {
        return shopId + ":" + requestId;
    }

    private String skuRequestKey(Long shopId, Long activityId, String requestId) {
        return shopId + ":" + activityId + ":" + requestId;
    }

    private String statusRequestKey(Long shopId, Long activityId, String requestId) {
        return shopId + ":" + activityId + ":" + requestId;
    }
}
