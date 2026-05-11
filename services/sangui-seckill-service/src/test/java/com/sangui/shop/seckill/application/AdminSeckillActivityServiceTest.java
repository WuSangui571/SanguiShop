package com.sangui.shop.seckill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.sangui.shop.seckill.domain.ActivityRepository;
import com.sangui.shop.seckill.domain.ActivityRepository.StatusRequestRecord;
import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient;
import com.sangui.shop.seckill.domain.SeckillActivity;
import com.sangui.shop.seckill.domain.SeckillActivitySku;
import com.sangui.shop.seckill.domain.SeckillActivityStatus;
import com.sangui.shop.seckill.domain.SeckillErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminSeckillActivityServiceTest {

    private InMemoryActivityRepository activityRepository;
    private FakeProductSkuSnapshotClient productClient;
    private AdminSeckillActivityService service;

    @BeforeEach
    void setUp() {
        activityRepository = new InMemoryActivityRepository();
        productClient = new FakeProductSkuSnapshotClient();
        service = new AdminSeckillActivityService(activityRepository, productClient);
    }

    private final SanguiPrincipal adminPrincipal = new SanguiPrincipal("90001", 1L, Set.of(),
            Set.of(SanguiPermissionConstants.SECKILL_ACTIVITY_ADMIN), "jwt-admin");
    private final SanguiPrincipal adminRolePrincipal = new SanguiPrincipal("90001", 1L, Set.of("ADMIN"),
            Set.of(), "jwt-admin");
    private final SanguiPrincipal opsOnlyPrincipal = new SanguiPrincipal("ops", 1L, Set.of(),
            Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN), "jwt-ops");

    @Test
    void adminPermissionAllowedToList() {
        AdminSeckillActivityPageResponse response = service.listActivities(adminPrincipal, 1, 20, null);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    void adminRoleAllowedToList() {
        AdminSeckillActivityPageResponse response = service.listActivities(adminRolePrincipal, 1, 20, null);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    void opsCompensationAloneDenied() {
        assertThatThrownBy(() -> service.listActivities(opsOnlyPrincipal, 1, 20, null))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.AUTH_FORBIDDEN.code());
                    assertThat(e.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void createActivityValidatesAndReturnsDetail() {
        AdminSeckillActivityDetailResponse response = service.createActivity(
                adminPrincipal,
                draftRequest("Spring flash sale", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-001"),
                "trace-create"
        );

        assertThat(response.activityName()).isEqualTo("Spring flash sale");
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.skuCount()).isEqualTo(1);
        assertThat(response.totalActivityStock()).isEqualTo(10);
        assertThat(response.skus().getFirst().productName()).isEqualTo("Running Shoe");
    }

    @Test
    void createTrimsActivityName() {
        AdminSeckillActivityDetailResponse response = service.createActivity(
                adminPrincipal,
                draftRequest("  Spring flash sale  ", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-trim"),
                "trace-trim"
        );
        assertThat(response.activityName()).isEqualTo("Spring flash sale");
    }

    @Test
    void createRejectsBlankActivityName() {
        assertThatThrownBy(() -> service.createActivity(
                adminPrincipal,
                draftRequest("  ", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-blank"),
                "trace-blank"
        )).isInstanceOfSatisfying(SanguiException.class, e -> {
            assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.code());
            assertThat(e.httpStatus()).isEqualTo(400);
        });
    }

    @Test
    void createRejectsInvalidTimeOrder() {
        assertThatThrownBy(() -> service.createActivity(
                adminPrincipal,
                draftRequest("Bad time", "2026-05-12T12:00:00+08:00", "2026-05-12T10:00:00+08:00", "req-time"),
                "trace-time"
        )).isInstanceOfSatisfying(SanguiException.class, e -> {
            assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.code());
            assertThat(e.httpStatus()).isEqualTo(400);
        });
    }

    @Test
    void createRejectsEqualTimeRange() {
        assertThatThrownBy(() -> service.createActivity(
                adminPrincipal,
                draftRequest("Equal time", "2026-05-12T10:00:00+08:00", "2026-05-12T10:00:00+08:00", "req-equal"),
                "trace-equal"
        )).isInstanceOfSatisfying(SanguiException.class, e -> {
            assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.code());
            assertThat(e.httpStatus()).isEqualTo(400);
        });
    }

    @Test
    void createRejectsMissingRequestId() {
        AdminSeckillActivityDraftRequest req = new AdminSeckillActivityDraftRequest(
                1L, "90001", "No requestId", null,
                "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00",
                "  ", List.of(skuItem())
        );
        assertThatThrownBy(() -> service.createActivity(adminPrincipal, req, "trace-noid"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.code());
                    assertThat(e.httpStatus()).isEqualTo(400);
                });
    }

    @Test
    void createDuplicateSameRequestIdAndPayloadIsIdempotent() {
        AdminSeckillActivityDraftRequest req = draftRequest("Idempotent", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-idem");
        AdminSeckillActivityDetailResponse first = service.createActivity(adminPrincipal, req, "trace-first");
        AdminSeckillActivityDetailResponse second = service.createActivity(adminPrincipal, req, "trace-second");

        assertThat(second.activityId()).isEqualTo(first.activityId());
        assertThat(second.activityName()).isEqualTo("Idempotent");
    }

    @Test
    void createDuplicateSameRequestIdDifferentPayloadFails() {
        service.createActivity(adminPrincipal,
                draftRequest("Original", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-conflict"),
                "trace-first");

        AdminSeckillActivityDraftRequest changedReq = draftRequest("Changed", "2026-05-13T10:00:00+08:00", "2026-05-13T12:00:00+08:00", "req-conflict");
        assertThatThrownBy(() -> service.createActivity(adminPrincipal, changedReq, "trace-second"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.IDEMPOTENCY_CONFLICT.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void createDuplicateSameRequestIdDifferentSkuPayloadFails() {
        service.createActivity(adminPrincipal,
                draftRequest("Original sku", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-sku-conflict"),
                "trace-first");

        AdminSeckillActivityDraftRequest changedReq = new AdminSeckillActivityDraftRequest(
                1L, "90001", "Original sku", null,
                "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00",
                "req-sku-conflict", List.of(new AdminSeckillActivitySkuItem(301L, 401L, 5, 49900L))
        );
        assertThatThrownBy(() -> service.createActivity(adminPrincipal, changedReq, "trace-second"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.IDEMPOTENCY_CONFLICT.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void createRejectsSkuProductMismatch() {
        AdminSeckillActivityDraftRequest req = new AdminSeckillActivityDraftRequest(
                1L, "90001", "Product mismatch", null,
                "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00",
                "req-product-mismatch", List.of(new AdminSeckillActivitySkuItem(999L, 401L, 10, 49900L))
        );
        assertThatThrownBy(() -> service.createActivity(adminPrincipal, req, "trace-product-mismatch"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.PRODUCT_SKU_NOT_FOUND.code());
                    assertThat(e.httpStatus()).isEqualTo(404);
                });
    }

    @Test
    void getActivityReturnsDetail() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Get test", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-get"),
                "trace-get");

        AdminSeckillActivityDetailResponse detail = service.getActivity(adminPrincipal, created.activityId());
        assertThat(detail.activityId()).isEqualTo(created.activityId());
        assertThat(detail.activityName()).isEqualTo("Get test");
        assertThat(detail.skus()).hasSize(1);
    }

    @Test
    void getActivityNotFoundReturns404() {
        assertThatThrownBy(() -> service.getActivity(adminPrincipal, 9999L))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND.code());
                    assertThat(e.httpStatus()).isEqualTo(404);
                });
    }

    @Test
    void crossShopCannotSeeActivity() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Cross shop", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-cross"),
                "trace-cross");

        SanguiPrincipal otherShop = new SanguiPrincipal("90002", 2L, Set.of(),
                Set.of(SanguiPermissionConstants.SECKILL_ACTIVITY_ADMIN), "jwt-other");
        assertThatThrownBy(() -> service.getActivity(otherShop, created.activityId()))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.SECKILL_ACTIVITY_NOT_FOUND.code());
                    assertThat(e.httpStatus()).isEqualTo(404);
                });
    }

    @Test
    void validStatusTransitionSucceeds() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Transition", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-trans"),
                "trace-trans");

        AdminSeckillActivityDetailResponse scheduled = service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("scheduled", "req-status-1"), "trace-status");
        assertThat(scheduled.status()).isEqualTo("scheduled");
    }

    @Test
    void invalidStatusTransitionFails() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Invalid trans", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-inv"),
                "trace-inv");

        assertThatThrownBy(() -> service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("ended", "req-status-inv"), "trace-inv"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.SECKILL_ACTIVITY_STATUS_INVALID.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void unknownStatusValueFails() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Unknown status", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-unk"),
                "trace-unk");

        assertThatThrownBy(() -> service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("unknown", "req-status-unk"), "trace-unk"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.SECKILL_ACTIVITY_STATUS_INVALID.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void statusReplaySameRequestIdIsIdempotent() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Status idem", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-sidem"),
                "trace-sidem");

        service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("scheduled", "req-same"), "trace-1");
        AdminSeckillActivityDetailResponse replay = service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("scheduled", "req-same"), "trace-2");
        assertThat(replay.status()).isEqualTo("scheduled");
    }

    @Test
    void statusReplaySameRequestIdWithDifferentTargetFails() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Status conflict", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-status-conflict"),
                "trace-status-conflict");

        service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("scheduled", "req-status-same"), "trace-1");

        assertThatThrownBy(() -> service.updateStatus(adminPrincipal, created.activityId(),
                new AdminSeckillActivityStatusUpdateRequest("active", "req-status-same"), "trace-2"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.IDEMPOTENCY_CONFLICT.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void bindSkuWithValidStockSucceeds() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Bind SKU", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-bind"),
                "trace-bind");

        AdminSeckillActivityDetailResponse afterBind = service.bindSku(adminPrincipal, created.activityId(),
                new AdminSeckillActivityBindSkuRequest(301L, 402L, 5, 39900L, "req-bind-sku"), "trace-bind-sku");
        assertThat(afterBind.skus()).hasSize(2);
    }

    @Test
    void bindSkuUpdatesExistingSku() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Update SKU", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-update-sku"),
                "trace-update-sku");

        AdminSeckillActivityDetailResponse afterBind = service.bindSku(adminPrincipal, created.activityId(),
                new AdminSeckillActivityBindSkuRequest(301L, 401L, 5, 39900L, "req-update-existing-sku"), "trace-update-existing-sku");
        assertThat(afterBind.skus()).hasSize(1);
        assertThat(afterBind.skus().getFirst().activityStock()).isEqualTo(5);
        assertThat(afterBind.skus().getFirst().seckillPriceCent()).isEqualTo(39900);
    }

    @Test
    void bindSkuStockExceedsAvailableFails() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Stock fail", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-stk"),
                "trace-stk");

        assertThatThrownBy(() -> service.bindSku(adminPrincipal, created.activityId(),
                new AdminSeckillActivityBindSkuRequest(301L, 401L, 100, 39900L, "req-bind-over"), "trace-over"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.PRODUCT_STOCK_NOT_ENOUGH.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void bindSkuNotFoundFails() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("SKU not found", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-nf"),
                "trace-nf");

        assertThatThrownBy(() -> service.bindSku(adminPrincipal, created.activityId(),
                new AdminSeckillActivityBindSkuRequest(999L, 999L, 5, 39900L, "req-bind-nf"), "trace-nf"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(SeckillErrorCode.PRODUCT_SKU_NOT_FOUND.code());
                    assertThat(e.httpStatus()).isEqualTo(404);
                });
    }

    @Test
    void bindSkuReplaySameRequestIdIsIdempotent() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Bind idem", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-bidem"),
                "trace-bidem");

        AdminSeckillActivityBindSkuRequest req = new AdminSeckillActivityBindSkuRequest(301L, 402L, 8, 29900L, "req-sku-same");
        AdminSeckillActivityDetailResponse first = service.bindSku(adminPrincipal, created.activityId(), req, "trace-1");
        AdminSeckillActivityDetailResponse second = service.bindSku(adminPrincipal, created.activityId(), req, "trace-2");
        assertThat(second.skus().size()).isEqualTo(first.skus().size());
    }

    @Test
    void bindSkuReplayWithChangedPayloadFails() {
        AdminSeckillActivityDetailResponse created = service.createActivity(adminPrincipal,
                draftRequest("Bind conflict", "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00", "req-bconf"),
                "trace-bconf");

        service.bindSku(adminPrincipal, created.activityId(),
                new AdminSeckillActivityBindSkuRequest(301L, 402L, 8, 29900L, "req-sku-conflict"), "trace-1");

        assertThatThrownBy(() -> service.bindSku(adminPrincipal, created.activityId(),
                new AdminSeckillActivityBindSkuRequest(301L, 402L, 10, 29900L, "req-sku-conflict"), "trace-2"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.IDEMPOTENCY_CONFLICT.code());
                    assertThat(e.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void negativeActivityStockRejectedByValidation() {
        AdminSeckillActivityDraftRequest req = new AdminSeckillActivityDraftRequest(
                1L, "90001", "Neg stock", null,
                "2026-05-12T10:00:00+08:00", "2026-05-12T12:00:00+08:00",
                "req-neg", List.of(new AdminSeckillActivitySkuItem(301L, 401L, -1, 49900L))
        );
        assertThatThrownBy(() -> service.createActivity(adminPrincipal, req, "trace-neg"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.VALIDATION_FAILED.code());
                });
    }

    private AdminSeckillActivityDraftRequest draftRequest(String name, String startsAt, String endsAt, String requestId) {
        return new AdminSeckillActivityDraftRequest(
                1L, "90001", name, null,
                startsAt, endsAt, requestId,
                List.of(skuItem())
        );
    }

    private AdminSeckillActivitySkuItem skuItem() {
        return new AdminSeckillActivitySkuItem(301L, 401L, 10, 49900L);
    }

    private static final class FakeProductSkuSnapshotClient implements ProductSkuSnapshotClient {
        private final Map<Long, ProductSkuSnapshot> skus = new LinkedHashMap<>();

        FakeProductSkuSnapshotClient() {
            skus.put(401L, new ProductSkuSnapshot(301L, "Running Shoe", 401L, "RS-42", "42", 59900L, 20L));
            skus.put(402L, new ProductSkuSnapshot(301L, "Running Shoe", 402L, "RS-43", "43", 59900L, 15L));
        }

        @Override
        public Optional<ProductSkuSnapshot> findBySkuId(Long shopId, Long skuId) {
            return Optional.ofNullable(skus.get(skuId));
        }
    }

    private static final class InMemoryActivityRepository implements ActivityRepository {
        private final AtomicLong nextId = new AtomicLong(1000);
        private final AtomicLong nextSkuId = new AtomicLong(2000);
        private final Map<Long, SeckillActivity> activitiesById = new LinkedHashMap<>();
        private final Map<String, SeckillActivity> activitiesByRequestId = new LinkedHashMap<>();
        private final Map<Long, List<SeckillActivitySku>> skusByActivityId = new LinkedHashMap<>();
        private final Map<String, SeckillActivitySku> skusByRequestId = new LinkedHashMap<>();
        private final Map<String, StatusRequestRecord> statusRequests = new LinkedHashMap<>();

        @Override
        public Optional<SeckillActivity> findById(Long shopId, Long activityId) {
            SeckillActivity activity = activitiesById.get(activityId);
            if (activity == null || !activity.shopId().equals(shopId)) {
                return Optional.empty();
            }
            return Optional.of(activity.withSkuBound(skusByActivityId.getOrDefault(activityId, List.of())));
        }

        @Override
        public Optional<SeckillActivity> findByRequestId(Long shopId, String requestId) {
            SeckillActivity activity = activitiesByRequestId.get(requestId);
            if (activity == null || !activity.shopId().equals(shopId)) {
                return Optional.empty();
            }
            return Optional.of(activity.withSkuBound(skusByActivityId.getOrDefault(activity.id(), List.of())));
        }

        @Override
        public List<SeckillActivity> findPage(Long shopId, String status, int offset, int size) {
            return activitiesById.values().stream()
                    .filter(a -> a.shopId().equals(shopId))
                    .filter(a -> status == null || a.status().value().equals(status))
                    .skip(offset)
                    .limit(size)
                    .map(a -> a.withSkuBound(skusByActivityId.getOrDefault(a.id(), List.of())))
                    .toList();
        }

        @Override
        public int count(Long shopId, String status) {
            return (int) activitiesById.values().stream()
                    .filter(a -> a.shopId().equals(shopId))
                    .filter(a -> status == null || a.status().value().equals(status))
                    .count();
        }

        @Override
        public Long create(SeckillActivity activity, List<SeckillActivitySku> skus) {
            Long id;
            if (activity.id() != null && activitiesById.containsKey(activity.id())) {
                id = activity.id();
            } else {
                id = nextId.incrementAndGet();
            }
            SeckillActivity stored = activity.withIdAndTimestamps(id, activity.traceId(), LocalDateTime.now());
            activitiesById.put(id, stored);
            activitiesByRequestId.put(activity.requestId(), stored);
            if (skus != null) {
                List<SeckillActivitySku> storedSkus = new ArrayList<>();
                for (SeckillActivitySku sku : skus) {
                    Long skuId = nextSkuId.incrementAndGet();
                    SeckillActivitySku storedSku = new SeckillActivitySku(
                            skuId, id, sku.productId(), sku.productName(),
                            sku.skuId(), sku.skuCode(), sku.skuName(),
                            sku.priceCent(), sku.seckillPriceCent(),
                            sku.availableStock(), sku.activityStock(), sku.soldCount(), sku.requestId()
                    );
                    storedSkus.add(storedSku);
                }
                skusByActivityId.put(id, storedSkus);
            }
            return id;
        }

        @Override
        public int updateActivityStatus(Long shopId, Long activityId, SeckillActivityStatus currentStatus, SeckillActivityStatus newStatus) {
            SeckillActivity activity = activitiesById.get(activityId);
            if (activity == null || !activity.shopId().equals(shopId) || activity.status() != currentStatus) {
                return 0;
            }
            SeckillActivity updated = activity.withStatus(newStatus);
            activitiesById.put(activityId, updated);
            activitiesByRequestId.put(updated.requestId(), updated);
            return 1;
        }

        @Override
        public int upsertSku(Long shopId, Long activityId, SeckillActivitySku sku) {
            List<SeckillActivitySku> currentSkus = skusByActivityId.computeIfAbsent(activityId, k -> new ArrayList<>());
            Long skuId = nextSkuId.incrementAndGet();
            SeckillActivitySku storedSku = new SeckillActivitySku(
                    skuId, activityId, sku.productId(), sku.productName(),
                    sku.skuId(), sku.skuCode(), sku.skuName(),
                    sku.priceCent(), sku.seckillPriceCent(),
                    sku.availableStock(), sku.activityStock(), sku.soldCount(), sku.requestId()
            );
            currentSkus.removeIf(existing -> existing.skuId().equals(storedSku.skuId()));
            currentSkus.add(storedSku);
            skusByRequestId.put(storedSku.requestId(), storedSku);
            return 1;
        }

        @Override
        public Optional<SeckillActivitySku> findSkuByRequestId(Long activityId, String requestId) {
            return Optional.ofNullable(skusByRequestId.get(requestId))
                    .filter(sku -> sku.activityId().equals(activityId));
        }

        @Override
        public Optional<StatusRequestRecord> findStatusRequestByRequestId(Long shopId, Long activityId, String requestId) {
            return Optional.ofNullable(statusRequests.get(shopId + ":" + activityId + ":" + requestId));
        }

        @Override
        public void saveStatusRequest(Long shopId, Long activityId, String requestId, SeckillActivityStatus targetStatus) {
            statusRequests.put(shopId + ":" + activityId + ":" + requestId,
                    new StatusRequestRecord(shopId, activityId, requestId, targetStatus));
        }
    }
}
