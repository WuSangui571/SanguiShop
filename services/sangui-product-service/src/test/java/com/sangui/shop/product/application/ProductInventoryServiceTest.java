package com.sangui.shop.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.product.client.dto.InventoryReserveItemRequest;
import com.sangui.shop.product.client.dto.InventoryReserveRequest;
import com.sangui.shop.product.domain.ProductDraft;
import com.sangui.shop.product.domain.ProductAdminListItem;
import com.sangui.shop.product.domain.ProductErrorCode;
import com.sangui.shop.product.domain.ProductInventoryReservationRecord;
import com.sangui.shop.product.domain.ProductInventoryReservationSnapshot;
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

class ProductInventoryServiceTest {

    private InMemoryProductRepository productRepository;
    private ProductInventoryService productInventoryService;
    private Long skuId;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productInventoryService = new ProductInventoryService(productRepository);
        Long productId = productRepository.seedProduct(1L, "10001", "Sneaker", ProductStatus.ACTIVE, List.of(
                new ProductSkuDraft("shoe-42", "42", 59900L, 5L)
        ));
        skuId = productRepository.firstSkuId(productId);
    }

    @Test
    void reserveIsIdempotentForSameReservationNoAndPayload() {
        InventoryReserveRequest request = new InventoryReserveRequest(
                1L,
                "ord:10001:req-001",
                List.of(new InventoryReserveItemRequest(skuId, 2))
        );

        ProductInventoryReservationSnapshot first = productInventoryService.reserve(request, "trace-1");
        ProductInventoryReservationSnapshot second = productInventoryService.reserve(request, "trace-2");

        assertThat(first.status()).isEqualTo(ProductInventoryReservationStatus.RESERVED);
        assertThat(second.status()).isEqualTo(ProductInventoryReservationStatus.RESERVED);
        assertThat(second.items()).hasSize(1);
        assertThat(productRepository.skusById.get(skuId).availableStock()).isEqualTo(3L);
        assertThat(productRepository.skusById.get(skuId).reservedStock()).isEqualTo(2L);
    }

    @Test
    void reserveRejectsInsufficientStock() {
        assertThatThrownBy(() -> productInventoryService.reserve(
                new InventoryReserveRequest(
                        1L,
                        "ord:10001:req-002",
                        List.of(new InventoryReserveItemRequest(skuId, 8))
                ),
                "trace-1"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(ProductErrorCode.PRODUCT_STOCK_NOT_ENOUGH.code());
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void confirmAndReleaseTransitionReservationStocks() {
        InventoryReserveRequest request = new InventoryReserveRequest(
                1L,
                "ord:10001:req-003",
                List.of(new InventoryReserveItemRequest(skuId, 2))
        );

        productInventoryService.reserve(request, "trace-reserve");
        ProductInventoryReservationSnapshot confirmed = productInventoryService.confirm(1L, "ord:10001:req-003", "trace-confirm");

        assertThat(confirmed.status()).isEqualTo(ProductInventoryReservationStatus.CONFIRMED);
        assertThat(productRepository.skusById.get(skuId).availableStock()).isEqualTo(3L);
        assertThat(productRepository.skusById.get(skuId).reservedStock()).isEqualTo(0L);

        productInventoryService.reserve(
                new InventoryReserveRequest(1L, "ord:10001:req-004", List.of(new InventoryReserveItemRequest(skuId, 1))),
                "trace-reserve-2"
        );
        ProductInventoryReservationSnapshot released = productInventoryService.release(1L, "ord:10001:req-004", "trace-release");

        assertThat(released.status()).isEqualTo(ProductInventoryReservationStatus.RELEASED);
        assertThat(productRepository.skusById.get(skuId).availableStock()).isEqualTo(3L);
        assertThat(productRepository.skusById.get(skuId).reservedStock()).isEqualTo(0L);
    }

    private static final class InMemoryProductRepository implements ProductRepository {

        private final AtomicLong nextProductId = new AtomicLong(10000);
        private final AtomicLong nextSkuId = new AtomicLong(20000);
        private final Map<Long, ProductRecord> productsById = new LinkedHashMap<>();
        private final Map<Long, ProductSkuRecord> skusById = new LinkedHashMap<>();
        private final Map<String, List<ProductInventoryReservationRecord>> reservationsByKey = new LinkedHashMap<>();

        @Override
        public com.sangui.shop.common.core.api.PageResponse<ProductListItem> listActiveProducts(
                Long shopId,
                com.sangui.shop.common.core.api.PageRequest pageRequest
        ) {
            return new com.sangui.shop.common.core.api.PageResponse<>(List.of(), 0L, pageRequest.page(), pageRequest.size());
        }

        @Override
        public com.sangui.shop.common.core.api.PageResponse<ProductAdminListItem> listAdminProducts(
                Long shopId,
                com.sangui.shop.common.core.api.PageRequest pageRequest,
                ProductStatus status
        ) {
            return new com.sangui.shop.common.core.api.PageResponse<>(List.of(), 0L, pageRequest.page(), pageRequest.size());
        }

        @Override
        public Optional<ProductSnapshot> findPublicProduct(Long shopId, Long productId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProductSnapshot> findAdminProduct(Long shopId, Long productId) {
            return Optional.empty();
        }

        @Override
        public List<ProductSkuRecord> findActiveSkus(Long shopId, List<Long> skuIds) {
            return skuIds.stream()
                    .map(skusById::get)
                    .filter(java.util.Objects::nonNull)
                    .filter(sku -> java.util.Objects.equals(productsById.get(sku.productId()).shopId(), shopId))
                    .filter(sku -> productsById.get(sku.productId()).status() == ProductStatus.ACTIVE)
                    .toList();
        }

        @Override
        public List<ProductInventoryReservationRecord> findReservationRecords(Long shopId, String reservationNo) {
            return List.copyOf(reservationsByKey.getOrDefault(key(shopId, reservationNo), List.of()));
        }

        @Override
        public int reserveSkuStock(Long shopId, Long skuId, int quantity) {
            ProductSkuRecord sku = skusById.get(skuId);
            if (sku == null || sku.availableStock() < quantity) {
                return 0;
            }
            skusById.put(skuId, new ProductSkuRecord(
                    sku.id(),
                    sku.productId(),
                    sku.skuCode(),
                    sku.skuName(),
                    sku.priceCent(),
                    sku.availableStock() - quantity,
                    sku.reservedStock() + quantity
            ));
            return 1;
        }

        @Override
        public int confirmReservedSkuStock(Long shopId, Long skuId, int quantity) {
            ProductSkuRecord sku = skusById.get(skuId);
            if (sku == null || sku.reservedStock() < quantity) {
                return 0;
            }
            skusById.put(skuId, new ProductSkuRecord(
                    sku.id(),
                    sku.productId(),
                    sku.skuCode(),
                    sku.skuName(),
                    sku.priceCent(),
                    sku.availableStock(),
                    sku.reservedStock() - quantity
            ));
            return 1;
        }

        @Override
        public int releaseReservedSkuStock(Long shopId, Long skuId, int quantity) {
            ProductSkuRecord sku = skusById.get(skuId);
            if (sku == null || sku.reservedStock() < quantity) {
                return 0;
            }
            skusById.put(skuId, new ProductSkuRecord(
                    sku.id(),
                    sku.productId(),
                    sku.skuCode(),
                    sku.skuName(),
                    sku.priceCent(),
                    sku.availableStock() + quantity,
                    sku.reservedStock() - quantity
            ));
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
            List<ProductInventoryReservationRecord> existing = reservationsByKey.get(key(shopId, reservationNo));
            if (existing == null || existing.isEmpty() || existing.getFirst().status() != currentStatus) {
                return 0;
            }
            reservationsByKey.put(
                    key(shopId, reservationNo),
                    existing.stream().map(item -> item.withStatus(nextStatus, traceId)).toList()
            );
            return existing.size();
        }

        @Override
        public void createReservationRecords(
                Long shopId,
                String reservationNo,
                List<ProductInventoryReservationRecord> records,
                ProductInventoryReservationStatus status,
                String traceId
        ) {
            reservationsByKey.put(
                    key(shopId, reservationNo),
                    records.stream().map(item -> item.withStatus(status, traceId)).toList()
            );
        }

        @Override
        public Long createProduct(Long shopId, String operatorUserId, ProductDraft draft, ProductStatus status) {
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
            for (ProductSkuDraft sku : draft.skus()) {
                Long skuId = nextSkuId.incrementAndGet();
                skusById.put(skuId, new ProductSkuRecord(
                        skuId,
                        productId,
                        sku.skuCode(),
                        sku.skuName(),
                        sku.priceCent(),
                        sku.availableStock(),
                        0L
                ));
            }
            return productId;
        }

        @Override
        public void updateProduct(Long shopId, Long productId, String operatorUserId, ProductDraft draft) {
        }

        @Override
        public void updateProductStatus(Long shopId, Long productId, String operatorUserId, ProductStatus status) {
        }

        @Override
        public int updateSkuAvailableStock(Long shopId, Long productId, Long skuId, String operatorUserId, Long availableStock) {
            return 1;
        }

        private Long seedProduct(Long shopId, String operatorUserId, String productName, ProductStatus status, List<ProductSkuDraft> skus) {
            return createProduct(shopId, operatorUserId, new ProductDraft(productName, productName + " description", skus), status);
        }

        private Long firstSkuId(Long productId) {
            return skusById.values().stream()
                    .filter(sku -> java.util.Objects.equals(sku.productId(), productId))
                    .findFirst()
                    .orElseThrow()
                    .id();
        }

        private String key(Long shopId, String reservationNo) {
            return shopId + "|" + reservationNo;
        }
    }
}
