package com.sangui.shop.seckill.domain;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository {

    Optional<SeckillActivity> findById(Long shopId, Long activityId);

    Optional<SeckillActivity> findByRequestId(Long shopId, String requestId);

    List<SeckillActivity> findPage(Long shopId, String status, int offset, int size);

    int count(Long shopId, String status);

    Long create(SeckillActivity activity, List<SeckillActivitySku> skus);

    int updateActivityStatus(Long shopId, Long activityId, SeckillActivityStatus currentStatus, SeckillActivityStatus newStatus);

    int upsertSku(Long shopId, Long activityId, SeckillActivitySku sku);

    Optional<SeckillActivitySku> findSkuByRequestId(Long shopId, Long activityId, String requestId);

    Optional<StatusRequestRecord> findStatusRequestByRequestId(Long shopId, Long activityId, String requestId);

    void saveStatusRequest(Long shopId, Long activityId, String requestId, SeckillActivityStatus targetStatus, String traceId);

    record StatusRequestRecord(Long shopId, Long activityId, String requestId, SeckillActivityStatus targetStatus) {
    }
}
