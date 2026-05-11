package com.sangui.shop.seckill.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityBindSkuRequest;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityDetailResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityDraftRequest;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityPageResponse;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivitySkuItem;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivityStatusUpdateRequest;
import com.sangui.shop.seckill.api.dto.AdminSeckillActivitySummaryResponse;
import com.sangui.shop.seckill.domain.ActivityRepository;
import com.sangui.shop.seckill.domain.ActivityRepository.StatusRequestRecord;
import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient;
import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient.ProductSkuSnapshot;
import com.sangui.shop.seckill.domain.SeckillActivity;
import com.sangui.shop.seckill.domain.SeckillActivitySku;
import com.sangui.shop.seckill.domain.SeckillActivityStatus;
import com.sangui.shop.seckill.domain.SeckillErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AdminSeckillActivityService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityRepository activityRepository;
    private final ProductSkuSnapshotClient productSkuSnapshotClient;

    public AdminSeckillActivityService(ActivityRepository activityRepository, ProductSkuSnapshotClient productSkuSnapshotClient) {
        this.activityRepository = activityRepository;
        this.productSkuSnapshotClient = productSkuSnapshotClient;
    }

    public AdminSeckillActivityPageResponse listActivities(SanguiPrincipal principal, int page, int size, String status) {
        requireAdmin(principal);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String normalizedStatus = trimToNull(status);
        if ("all".equalsIgnoreCase(normalizedStatus)) {
            normalizedStatus = null;
        }
        int offset = (normalizedPage - 1) * normalizedSize;
        OffsetDateTime serverTime = OffsetDateTime.now();
        List<SeckillActivity> activities = activityRepository.findPage(principal.shopId(), normalizedStatus, offset, normalizedSize);
        int total = activityRepository.count(principal.shopId(), normalizedStatus);
        return new AdminSeckillActivityPageResponse(
                normalizedPage,
                normalizedSize,
                total,
                activities.stream().map(a -> AdminSeckillActivitySummaryResponse.from(a, serverTime)).toList()
        );
    }

    public AdminSeckillActivityDetailResponse getActivity(SanguiPrincipal principal, Long activityId) {
        requireAdmin(principal);
        SeckillActivity activity = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));
        return AdminSeckillActivityDetailResponse.from(activity, OffsetDateTime.now());
    }


    public AdminSeckillActivityDetailResponse createActivity(SanguiPrincipal principal, AdminSeckillActivityDraftRequest request, String traceId) {
        requireAdmin(principal);
        String requestId = requireText(request.requestId());
        String activityName = requireText(request.activityName());
        OffsetDateTime startsAt = parseIsoTime(requireText(request.startsAt()));
        OffsetDateTime endsAt = parseIsoTime(requireText(request.endsAt()));
        validateTimeOrder(startsAt, endsAt);

        {
            Optional<SeckillActivity> existing = activityRepository.findByRequestId(principal.shopId(), requestId);
            if (existing.isPresent()) {
                SeckillActivity act = existing.get();
                if (sameDraftPayload(act, request, activityName, startsAt, endsAt)) {
                    return AdminSeckillActivityDetailResponse.from(act, OffsetDateTime.now());
                }
                throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
            }
        }

        List<SeckillActivitySku> activitySkus = new ArrayList<>();
        if (request.skus() != null) {
            for (var item : request.skus()) {
                ProductSkuSnapshot skuSnapshot = resolveSku(principal.shopId(), item.skuId());
                validateRequestedProduct(skuSnapshot, item.productId());
                validateStockAvailable(skuSnapshot, item.activityStock());
                activitySkus.add(new SeckillActivitySku(
                        null, null,
                        skuSnapshot.productId(), skuSnapshot.productName(),
                        skuSnapshot.skuId(), skuSnapshot.skuCode(), skuSnapshot.skuName(),
                        skuSnapshot.priceCent(), item.seckillPriceCent(),
                        skuSnapshot.availableStock(), item.activityStock(), 0L, null
                ));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillActivity activity = new SeckillActivity(
                null, principal.shopId(), activityName,
                trimToNull(request.description()),
                SeckillActivityStatus.DRAFT,
                startsAt.toLocalDateTime(), endsAt.toLocalDateTime(),
                requestId, traceId, activitySkus, now, now
        );
        Long activityId = activityRepository.create(activity, activitySkus);
        SeckillActivity saved = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));
        return AdminSeckillActivityDetailResponse.from(saved, OffsetDateTime.now());
    }


    public AdminSeckillActivityDetailResponse updateActivity(SanguiPrincipal principal, Long activityId, AdminSeckillActivityDraftRequest request, String traceId) {
        requireAdmin(principal);
        SeckillActivity existing = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));

        String requestId = requireText(request.requestId());
        if (!Objects.equals(existing.requestId(), requestId)) {
            activityRepository.findByRequestId(principal.shopId(), requestId).ifPresent(duplicate -> {
                throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
            });
        } else {
            if (!sameDraftPayload(existing, request, requireText(request.activityName()), parseIsoTime(requireText(request.startsAt())), parseIsoTime(requireText(request.endsAt())))) {
                throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
            }
        }

        String activityName = requireText(request.activityName());
        OffsetDateTime startsAt = parseIsoTime(requireText(request.startsAt()));
        OffsetDateTime endsAt = parseIsoTime(requireText(request.endsAt()));
        validateTimeOrder(startsAt, endsAt);

        List<SeckillActivitySku> activitySkus = new ArrayList<>();
        if (request.skus() != null) {
            for (var item : request.skus()) {
                ProductSkuSnapshot skuSnapshot = resolveSku(principal.shopId(), item.skuId());
                validateRequestedProduct(skuSnapshot, item.productId());
                validateStockAvailable(skuSnapshot, item.activityStock());
                activitySkus.add(new SeckillActivitySku(
                        null, activityId,
                        skuSnapshot.productId(), skuSnapshot.productName(),
                        skuSnapshot.skuId(), skuSnapshot.skuCode(), skuSnapshot.skuName(),
                        skuSnapshot.priceCent(), item.seckillPriceCent(),
                        skuSnapshot.availableStock(), item.activityStock(), 0L, null
                ));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillActivity updated = new SeckillActivity(
                activityId, principal.shopId(), activityName,
                trimToNull(request.description()),
                existing.status(),
                startsAt.toLocalDateTime(), endsAt.toLocalDateTime(),
                requestId, traceId, activitySkus, existing.createdAt(), now
        );
        activityRepository.create(updated, activitySkus);
        SeckillActivity saved = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));
        return AdminSeckillActivityDetailResponse.from(saved, OffsetDateTime.now());
    }


    public AdminSeckillActivityDetailResponse updateStatus(SanguiPrincipal principal, Long activityId, AdminSeckillActivityStatusUpdateRequest request, String traceId) {
        requireAdmin(principal);
        SeckillActivity activity = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));

        String requestId = requireText(request.requestId());
        SeckillActivityStatus targetStatus;
        try {
            targetStatus = SeckillActivityStatus.fromValue(requireText(request.status()));
        } catch (IllegalArgumentException e) {
            throw new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_STATUS_INVALID, 409);
        }

        Optional<StatusRequestRecord> previousStatusRequest =
                activityRepository.findStatusRequestByRequestId(principal.shopId(), activityId, requestId);
        if (previousStatusRequest.isPresent()) {
            if (previousStatusRequest.get().targetStatus() == targetStatus) {
                return AdminSeckillActivityDetailResponse.from(activity, OffsetDateTime.now());
            }
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }

        if (activity.status() == targetStatus) {
            activityRepository.saveStatusRequest(principal.shopId(), activityId, requestId, targetStatus);
            return AdminSeckillActivityDetailResponse.from(activity, OffsetDateTime.now());
        }

        if (!activity.status().canTransitionTo(targetStatus)) {
            throw new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_STATUS_INVALID, 409);
        }

        int updated = activityRepository.updateActivityStatus(principal.shopId(), activityId, activity.status(), targetStatus);
        if (updated == 0) {
            throw new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_STATUS_INVALID, 409);
        }
        activityRepository.saveStatusRequest(principal.shopId(), activityId, requestId, targetStatus);

        SeckillActivity saved = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));
        return AdminSeckillActivityDetailResponse.from(saved, OffsetDateTime.now());
    }


    public AdminSeckillActivityDetailResponse bindSku(SanguiPrincipal principal, Long activityId, AdminSeckillActivityBindSkuRequest request, String traceId) {
        requireAdmin(principal);
        SeckillActivity activity = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));

        String requestId = requireText(request.requestId());
        Optional<SeckillActivitySku> existingSku = activityRepository.findSkuByRequestId(activityId, requestId);
        if (existingSku.isPresent()) {
            SeckillActivitySku sku = existingSku.get();
            if (sku.skuId().equals(request.skuId()) && sku.activityStock() == request.activityStock()
                    && (request.seckillPriceCent() == null || sku.seckillPriceCent().equals(request.seckillPriceCent()))) {
                return AdminSeckillActivityDetailResponse.from(
                        activityRepository.findById(principal.shopId(), activityId)
                                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404)),
                        OffsetDateTime.now()
                );
            }
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }

        ProductSkuSnapshot skuSnapshot = resolveSku(principal.shopId(), request.skuId());
        validateRequestedProduct(skuSnapshot, request.productId());
        validateStockAvailable(skuSnapshot, request.activityStock());
        long seckillPriceCent = request.seckillPriceCent() != null ? request.seckillPriceCent() : skuSnapshot.priceCent();
        if (seckillPriceCent <= 0) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }

        SeckillActivitySku newSku = new SeckillActivitySku(
                null, activityId,
                skuSnapshot.productId(), skuSnapshot.productName(),
                skuSnapshot.skuId(), skuSnapshot.skuCode(), skuSnapshot.skuName(),
                skuSnapshot.priceCent(), seckillPriceCent,
                skuSnapshot.availableStock(), request.activityStock(), 0L, requestId
        );
        activityRepository.upsertSku(principal.shopId(), activityId, newSku);

        SeckillActivity saved = activityRepository.findById(principal.shopId(), activityId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND, 404));
        return AdminSeckillActivityDetailResponse.from(saved, OffsetDateTime.now());
    }

    private void requireAdmin(SanguiPrincipal principal) {
        boolean hasAdminRole = principal.roles() != null && principal.roles().contains(ADMIN_ROLE);
        boolean hasSeckillActivityPermission = principal.permissions() != null
                && principal.permissions().contains(SanguiPermissionConstants.SECKILL_ACTIVITY_ADMIN);
        if (!hasAdminRole && !hasSeckillActivityPermission) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
    }

    private ProductSkuSnapshot resolveSku(Long shopId, Long skuId) {
        return productSkuSnapshotClient.findBySkuId(shopId, skuId)
                .orElseThrow(() -> new SanguiException(SeckillErrorCode.PRODUCT_SKU_NOT_FOUND, 404));
    }

    private void validateRequestedProduct(ProductSkuSnapshot sku, Long productId) {
        if (!Objects.equals(sku.productId(), productId)) {
            throw new SanguiException(SeckillErrorCode.PRODUCT_SKU_NOT_FOUND, 404);
        }
    }

    private void validateStockAvailable(ProductSkuSnapshot sku, long activityStock) {
        if (activityStock < 0) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        if (activityStock > sku.availableStock()) {
            throw new SanguiException(SeckillErrorCode.PRODUCT_STOCK_NOT_ENOUGH, 409);
        }
    }

    private OffsetDateTime parseIsoTime(String value) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private void validateTimeOrder(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
    }

    private boolean sameDraftPayload(SeckillActivity existing, AdminSeckillActivityDraftRequest request, String activityName,
                                     OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return Objects.equals(existing.activityName(), activityName)
                && Objects.equals(existing.description(), trimToNull(request.description()))
                && Objects.equals(toOffsetDateTime(existing.startsAt()), startsAt)
                && Objects.equals(toOffsetDateTime(existing.endsAt()), endsAt)
                && sameSkuItems(existing.skus(), request.skus());
    }

    private boolean sameSkuItems(List<SeckillActivitySku> existingSkus, List<AdminSeckillActivitySkuItem> requestedSkus) {
        List<SeckillActivitySku> normalizedExisting = existingSkus == null ? List.of() : existingSkus;
        List<AdminSeckillActivitySkuItem> normalizedRequested = requestedSkus == null ? List.of() : requestedSkus;
        if (normalizedExisting.size() != normalizedRequested.size()) {
            return false;
        }
        for (int i = 0; i < normalizedExisting.size(); i++) {
            SeckillActivitySku existing = normalizedExisting.get(i);
            AdminSeckillActivitySkuItem requested = normalizedRequested.get(i);
            if (!Objects.equals(existing.productId(), requested.productId())
                    || !Objects.equals(existing.skuId(), requested.skuId())
                    || existing.activityStock() != requested.activityStock()
                    || existing.seckillPriceCent() != requested.seckillPriceCent()) {
                return false;
            }
        }
        return true;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String requireText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
