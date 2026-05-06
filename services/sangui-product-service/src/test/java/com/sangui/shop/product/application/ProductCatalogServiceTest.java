package com.sangui.shop.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.api.PageRequest;
import com.sangui.shop.common.core.api.PageResponse;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.product.api.dto.CreateProductRequest;
import com.sangui.shop.product.api.dto.ProductAdminSummaryResponse;
import com.sangui.shop.product.api.dto.ProductDetailResponse;
import com.sangui.shop.product.api.dto.ProductSkuStockAdjustmentRequest;
import com.sangui.shop.product.api.dto.ProductStatusUpdateRequest;
import com.sangui.shop.product.api.dto.UpdateProductRequest;
import com.sangui.shop.product.api.dto.UpsertProductSkuRequest;
import com.sangui.shop.product.domain.ProductDraft;
import com.sangui.shop.product.domain.ProductAdminListItem;
import com.sangui.shop.product.domain.ProductInventoryReservationRecord;
import com.sangui.shop.product.domain.ProductInventoryReservationStatus;
import com.sangui.shop.product.domain.ProductListItem;
import com.sangui.shop.product.domain.ProductRecord;
import com.sangui.shop.product.domain.ProductRepository;
import com.sangui.shop.product.domain.ProductSkuDraft;
import com.sangui.shop.product.domain.ProductSkuRecord;
import com.sangui.shop.product.domain.ProductSnapshot;
import com.sangui.shop.product.domain.ProductStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductCatalogServiceTest {

    private static final SanguiPrincipal ADMIN_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("ADMIN"),
            java.util.Set.of("product:write"),
            "jwt-admin"
    );

    private InMemoryProductRepository productRepository;
    private ProductCatalogService productCatalogService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productCatalogService = new ProductCatalogService(productRepository, 1L);
    }

    @Test
    void createProductUsesPrincipalIdentityInsteadOfBodyShopIdAndUserId() {
        ProductDetailResponse response = productCatalogService.createProduct(
                ADMIN_PRINCIPAL,
                new CreateProductRequest(
                        999L,
                        "spoof-user",
                        "Winter Coat",
                        "Warm and waterproof",
                        List.of(new UpsertProductSkuRequest("coat-black-m", "Black M", 129900L, 20L))
                )
        );

        assertThat(response.productId()).isEqualTo(10001L);
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.skus().getFirst().availableStock()).isEqualTo(20L);
        assertThat(response.skus().getFirst().reservedStock()).isEqualTo(0L);
        assertThat(productRepository.lastCreatedShopId).isEqualTo(1L);
        assertThat(productRepository.lastOperatorUserId).isEqualTo("10001");
        assertThat(productRepository.productsById.get(response.productId()).shopId()).isEqualTo(1L);
    }

    @Test
    void listProductsReturnsOnlyActiveItems() {
        productRepository.seedProduct(1L, "10001", "Draft Product", ProductStatus.DRAFT, List.of(
                new ProductSkuDraft("draft-sku", "Draft SKU", 1000L, 5L)
        ));
        productRepository.seedProduct(1L, "10001", "Active Product", ProductStatus.ACTIVE, List.of(
                new ProductSkuDraft("active-sku", "Active SKU", 2000L, 8L)
        ));

        PageResponse<?> response = productCatalogService.listProducts(new PageRequest(1, 20));

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void listAdminProductsIncludesStockOverviewAndStatusFilter() {
        productRepository.seedProduct(1L, "10001", "Draft Product", ProductStatus.DRAFT, List.of(
                new ProductSkuDraft("draft-sku", "Draft SKU", 1000L, 5L)
        ));
        Long activeProductId = productRepository.seedProduct(1L, "10001", "Active Product", ProductStatus.ACTIVE, List.of(
                new ProductSkuDraft("active-sku-a", "Active SKU A", 2000L, 8L),
                new ProductSkuDraft("active-sku-b", "Active SKU B", 4000L, 3L)
        ));

        PageResponse<ProductAdminSummaryResponse> response = productCatalogService.listAdminProducts(
                ADMIN_PRINCIPAL,
                new PageRequest(1, 20),
                "active"
        );

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().productId()).isEqualTo(activeProductId);
        assertThat(response.items().getFirst().skuCount()).isEqualTo(2L);
        assertThat(response.items().getFirst().availableStockTotal()).isEqualTo(11L);
        assertThat(response.items().getFirst().reservedStockTotal()).isEqualTo(0L);
    }

    @Test
    void publishProductTransitionsDraftToActive() {
        Long productId = productRepository.seedProduct(1L, "10001", "Sneaker", ProductStatus.DRAFT, List.of(
                new ProductSkuDraft("shoe-42", "42", 59900L, 10L)
        ));

        ProductDetailResponse response = productCatalogService.publishProduct(ADMIN_PRINCIPAL, productId);

        assertThat(response.status()).isEqualTo("active");
        assertThat(productRepository.productsById.get(productId).status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(productRepository.lastOperatorUserId).isEqualTo("10001");
    }

    @Test
    void publishRejectsInvalidState() {
        Long productId = productRepository.seedProduct(1L, "10001", "Sneaker", ProductStatus.ACTIVE, List.of(
                new ProductSkuDraft("shoe-42", "42", 59900L, 10L)
        ));

        assertThatThrownBy(() -> productCatalogService.publishProduct(ADMIN_PRINCIPAL, productId))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("PRODUCT_STATUS_INVALID");
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void updateProductStatusAndSkuStockUseAdminScope() {
        Long productId = productRepository.seedProduct(1L, "10001", "Sneaker", ProductStatus.DRAFT, List.of(
                new ProductSkuDraft("shoe-42", "42", 59900L, 10L)
        ));
        Long skuId = productRepository.firstSkuId(productId);

        ProductDetailResponse activated = productCatalogService.updateProductStatus(
                ADMIN_PRINCIPAL,
                productId,
                new ProductStatusUpdateRequest("inactive", "req-status-1")
        );
        ProductDetailResponse adjusted = productCatalogService.adjustSkuStock(
                ADMIN_PRINCIPAL,
                productId,
                skuId,
                new ProductSkuStockAdjustmentRequest(25L, "req-stock-1")
        );

        assertThat(activated.status()).isEqualTo("inactive");
        assertThat(adjusted.skus().getFirst().availableStock()).isEqualTo(25L);
        assertThat(productRepository.productsById.get(productId).status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(productRepository.skusByProductId.get(productId).getFirst().availableStock()).isEqualTo(25L);
    }

    @Test
    void listActiveSkuSnapshotsOnlyReturnsActiveProductSkus() {
        Long draftProductId = productRepository.seedProduct(1L, "10001", "Draft Product", ProductStatus.DRAFT, List.of(
                new ProductSkuDraft("draft-sku", "Draft SKU", 1000L, 5L)
        ));
        Long activeProductId = productRepository.seedProduct(1L, "10001", "Active Product", ProductStatus.ACTIVE, List.of(
                new ProductSkuDraft("active-sku", "Active SKU", 2000L, 8L)
        ));

        List<ProductSkuRecord> snapshots = productCatalogService.listActiveSkuSnapshots(1L, List.of(
                productRepository.firstSkuId(draftProductId),
                productRepository.firstSkuId(activeProductId)
        ));

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().skuCode()).isEqualTo("active-sku");
    }

    @Test
    void createRejectsNonAdminPrincipal() {
        SanguiPrincipal nonAdmin = new SanguiPrincipal("10002", 1L, java.util.Set.of("USER"), java.util.Set.of(), "jwt");

        assertThatThrownBy(() -> productCatalogService.createProduct(
                nonAdmin,
                new CreateProductRequest(
                        1L,
                        "10002",
                        "T-Shirt",
                        "Soft cotton",
                        List.of(new UpsertProductSkuRequest("tee-s", "Small", 9900L, 3L))
                )
        ))
                .isInstanceOfSatisfying(SanguiException.class, exception -> {
                    assertThat(exception.errorCode().code()).isEqualTo("AUTH_FORBIDDEN");
                    assertThat(exception.httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void updateReplacesSkuSet() {
        Long productId = productRepository.seedProduct(1L, "10001", "Sneaker", ProductStatus.DRAFT, List.of(
                new ProductSkuDraft("shoe-42", "42", 59900L, 10L)
        ));

        ProductDetailResponse response = productCatalogService.updateProduct(
                ADMIN_PRINCIPAL,
                productId,
                new UpdateProductRequest(
                        2L,
                        "spoof-user",
                        "Sneaker Pro",
                        "Updated model",
                        List.of(
                                new UpsertProductSkuRequest("shoe-43", "43", 69900L, 15L),
                                new UpsertProductSkuRequest("shoe-44", "44", 69900L, 12L)
                        )
                )
        );

        assertThat(response.productName()).isEqualTo("Sneaker Pro");
        assertThat(response.skus()).hasSize(2);
        assertThat(productRepository.productsById.get(productId).productName()).isEqualTo("Sneaker Pro");
    }

    private static final class InMemoryProductRepository implements ProductRepository {

        private final AtomicLong nextProductId = new AtomicLong(10000);
        private final AtomicLong nextSkuId = new AtomicLong(20000);
        private final Map<Long, ProductRecord> productsById = new LinkedHashMap<>();
        private final Map<Long, List<ProductSkuRecord>> skusByProductId = new LinkedHashMap<>();
        private Long lastCreatedShopId;
        private String lastOperatorUserId;

        @Override
        public PageResponse<ProductListItem> listActiveProducts(Long shopId, PageRequest pageRequest) {
            List<ProductListItem> items = productsById.values().stream()
                    .filter(product -> product.shopId().equals(shopId))
                    .filter(product -> product.status() == ProductStatus.ACTIVE)
                    .map(product -> {
                        List<ProductSkuRecord> skus = skusByProductId.getOrDefault(product.id(), List.of());
                        long minPrice = skus.stream().mapToLong(ProductSkuRecord::priceCent).min().orElse(0L);
                        long maxPrice = skus.stream().mapToLong(ProductSkuRecord::priceCent).max().orElse(0L);
                        return new ProductListItem(
                                product.id(),
                                product.productName(),
                                product.productDescription(),
                                minPrice,
                                maxPrice,
                                product.status()
                        );
                    })
                    .toList();
            return new PageResponse<>(items, items.size(), pageRequest.page(), pageRequest.size());
        }

        @Override
        public PageResponse<ProductAdminListItem> listAdminProducts(Long shopId, PageRequest pageRequest, ProductStatus status) {
            List<ProductAdminListItem> items = productsById.values().stream()
                    .filter(product -> product.shopId().equals(shopId))
                    .filter(product -> status == null || product.status() == status)
                    .map(product -> {
                        List<ProductSkuRecord> skus = skusByProductId.getOrDefault(product.id(), List.of());
                        long minPrice = skus.stream().mapToLong(ProductSkuRecord::priceCent).min().orElse(0L);
                        long maxPrice = skus.stream().mapToLong(ProductSkuRecord::priceCent).max().orElse(0L);
                        long availableStockTotal = skus.stream().mapToLong(ProductSkuRecord::availableStock).sum();
                        long reservedStockTotal = skus.stream().mapToLong(ProductSkuRecord::reservedStock).sum();
                        return new ProductAdminListItem(
                                product.id(),
                                product.productName(),
                                product.productDescription(),
                                minPrice,
                                maxPrice,
                                product.status(),
                                (long) skus.size(),
                                availableStockTotal,
                                reservedStockTotal
                        );
                    })
                    .toList();
            return new PageResponse<>(items, items.size(), pageRequest.page(), pageRequest.size());
        }

        @Override
        public Optional<ProductSnapshot> findPublicProduct(Long shopId, Long productId) {
            ProductRecord product = productsById.get(productId);
            if (product == null || !product.shopId().equals(shopId) || product.status() != ProductStatus.ACTIVE) {
                return Optional.empty();
            }
            return Optional.of(new ProductSnapshot(product, List.copyOf(skusByProductId.getOrDefault(productId, List.of()))));
        }

        @Override
        public Optional<ProductSnapshot> findAdminProduct(Long shopId, Long productId) {
            ProductRecord product = productsById.get(productId);
            if (product == null || !product.shopId().equals(shopId)) {
                return Optional.empty();
            }
            return Optional.of(new ProductSnapshot(product, List.copyOf(skusByProductId.getOrDefault(productId, List.of()))));
        }

        @Override
        public List<ProductSkuRecord> findActiveSkus(Long shopId, List<Long> skuIds) {
            return productsById.values().stream()
                    .filter(product -> product.shopId().equals(shopId))
                    .filter(product -> product.status() == ProductStatus.ACTIVE)
                    .flatMap(product -> skusByProductId.getOrDefault(product.id(), List.of()).stream())
                    .filter(sku -> skuIds.contains(sku.id()))
                    .toList();
        }

        @Override
        public List<ProductInventoryReservationRecord> findReservationRecords(Long shopId, String reservationNo) {
            return List.of();
        }

        @Override
        public int reserveSkuStock(Long shopId, Long skuId, int quantity) {
            return 1;
        }

        @Override
        public int confirmReservedSkuStock(Long shopId, Long skuId, int quantity) {
            return 1;
        }

        @Override
        public int releaseReservedSkuStock(Long shopId, Long skuId, int quantity) {
            return 1;
        }

        @Override
        public int updateReservationStatus(
                Long shopId,
                String reservationNo,
                ProductInventoryReservationStatus currentStatus,
                ProductInventoryReservationStatus nextStatus,
                String traceId
        ) {
            return 0;
        }

        @Override
        public void createReservationRecords(
                Long shopId,
                String reservationNo,
                List<ProductInventoryReservationRecord> records,
                ProductInventoryReservationStatus status,
                String traceId
        ) {
        }

        @Override
        public Long createProduct(Long shopId, String operatorUserId, ProductDraft draft, ProductStatus status) {
            lastCreatedShopId = shopId;
            lastOperatorUserId = operatorUserId;
            Long productId = nextProductId.incrementAndGet();
            productsById.put(productId, new ProductRecord(
                    productId,
                    shopId,
                    draft.productName(),
                    draft.productDescription(),
                    status,
                    operatorUserId,
                    operatorUserId
            ));
            skusByProductId.put(productId, createSkuRecords(productId, draft.skus()));
            return productId;
        }

        @Override
        public void updateProduct(Long shopId, Long productId, String operatorUserId, ProductDraft draft) {
            lastOperatorUserId = operatorUserId;
            ProductRecord existing = productsById.get(productId);
            productsById.put(productId, new ProductRecord(
                    existing.id(),
                    shopId,
                    draft.productName(),
                    draft.productDescription(),
                    existing.status(),
                    existing.createdBy(),
                    operatorUserId
            ));
            skusByProductId.put(productId, createSkuRecords(productId, draft.skus()));
        }

        @Override
        public void updateProductStatus(Long shopId, Long productId, String operatorUserId, ProductStatus status) {
            lastOperatorUserId = operatorUserId;
            ProductRecord existing = productsById.get(productId);
            productsById.put(productId, new ProductRecord(
                    existing.id(),
                    shopId,
                    existing.productName(),
                    existing.productDescription(),
                    status,
                    existing.createdBy(),
                    operatorUserId
            ));
        }

        @Override
        public int updateSkuAvailableStock(Long shopId, Long productId, Long skuId, String operatorUserId, Long availableStock) {
            lastOperatorUserId = operatorUserId;
            List<ProductSkuRecord> skus = new ArrayList<>(skusByProductId.getOrDefault(productId, List.of()));
            int skuIndex = -1;
            for (int index = 0; index < skus.size(); index++) {
                if (skus.get(index).id().equals(skuId)) {
                    skuIndex = index;
                    break;
                }
            }
            if (skuIndex < 0) {
                return 0;
            }
            ProductSkuRecord existing = skus.get(skuIndex);
            skus.set(skuIndex, new ProductSkuRecord(
                    existing.id(),
                    existing.productId(),
                    existing.skuCode(),
                    existing.skuName(),
                    existing.priceCent(),
                    availableStock,
                    existing.reservedStock()
            ));
            skusByProductId.put(productId, skus);
            return 1;
        }

        private Long seedProduct(Long shopId, String operatorUserId, String productName, ProductStatus status, List<ProductSkuDraft> skus) {
            return createProduct(shopId, operatorUserId, new ProductDraft(productName, productName + " description", skus), status);
        }

        private Long firstSkuId(Long productId) {
            return skusByProductId.getOrDefault(productId, List.of()).getFirst().id();
        }

        private List<ProductSkuRecord> createSkuRecords(Long productId, List<ProductSkuDraft> skus) {
            List<ProductSkuRecord> result = new ArrayList<>();
            for (ProductSkuDraft sku : skus) {
                result.add(new ProductSkuRecord(
                        nextSkuId.incrementAndGet(),
                        productId,
                        sku.skuCode(),
                        sku.skuName(),
                        sku.priceCent(),
                        sku.availableStock(),
                        0L
                ));
            }
            return result;
        }
    }
}
