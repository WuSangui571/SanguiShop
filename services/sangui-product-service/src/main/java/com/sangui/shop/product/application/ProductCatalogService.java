package com.sangui.shop.product.application;

import com.sangui.shop.common.core.api.PageRequest;
import com.sangui.shop.common.core.api.PageResponse;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.product.api.dto.CreateProductRequest;
import com.sangui.shop.product.api.dto.ProductDetailResponse;
import com.sangui.shop.product.api.dto.ProductSkuResponse;
import com.sangui.shop.product.api.dto.ProductSummaryResponse;
import com.sangui.shop.product.api.dto.UpdateProductRequest;
import com.sangui.shop.product.api.dto.UpsertProductSkuRequest;
import com.sangui.shop.product.domain.ProductDraft;
import com.sangui.shop.product.domain.ProductErrorCode;
import com.sangui.shop.product.domain.ProductListItem;
import com.sangui.shop.product.domain.ProductRecord;
import com.sangui.shop.product.domain.ProductRepository;
import com.sangui.shop.product.domain.ProductSkuDraft;
import com.sangui.shop.product.domain.ProductSnapshot;
import com.sangui.shop.product.domain.ProductSkuRecord;
import com.sangui.shop.product.domain.ProductStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final ProductRepository productRepository;
    private final Long defaultShopId;

    public ProductCatalogService(
            ProductRepository productRepository,
            @Value("${sangui.shop.default-shop-id:1}") Long defaultShopId
    ) {
        this.productRepository = productRepository;
        this.defaultShopId = defaultShopId;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listProducts(PageRequest pageRequest) {
        PageResponse<ProductListItem> page = productRepository.listActiveProducts(defaultShopId, pageRequest);
        List<ProductSummaryResponse> items = page.items().stream()
                .map(item -> new ProductSummaryResponse(
                        item.productId(),
                        item.productName(),
                        item.productDescription(),
                        item.minPriceCent(),
                        item.maxPriceCent(),
                        item.status().value()
                ))
                .toList();
        return new PageResponse<>(items, page.total(), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long productId) {
        ProductSnapshot snapshot = productRepository.findPublicProduct(defaultShopId, productId)
                .orElseThrow(() -> new SanguiException(ProductErrorCode.PRODUCT_NOT_FOUND, 404));
        return toDetailResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public List<ProductSkuRecord> listActiveSkuSnapshots(Long shopId, List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        return productRepository.findActiveSkus(shopId, skuIds);
    }

    @Transactional
    public ProductDetailResponse createProduct(SanguiPrincipal principal, CreateProductRequest request) {
        requireAdmin(principal);
        ProductDraft draft = toDraft(request.productName(), request.productDescription(), request.skus());
        try {
            Long productId = productRepository.createProduct(
                    principal.shopId(),
                    principal.userId(),
                    draft,
                    ProductStatus.DRAFT
            );
            return getAdminProduct(principal.shopId(), productId);
        } catch (DuplicateKeyException exception) {
            throw new SanguiException(ProductErrorCode.PRODUCT_SKU_CODE_EXISTS, 409);
        }
    }

    @Transactional
    public ProductDetailResponse updateProduct(SanguiPrincipal principal, Long productId, UpdateProductRequest request) {
        requireAdmin(principal);
        requireAdminSnapshot(principal.shopId(), productId);
        ProductDraft draft = toDraft(request.productName(), request.productDescription(), request.skus());
        try {
            productRepository.updateProduct(principal.shopId(), productId, principal.userId(), draft);
            return getAdminProduct(principal.shopId(), productId);
        } catch (DuplicateKeyException exception) {
            throw new SanguiException(ProductErrorCode.PRODUCT_SKU_CODE_EXISTS, 409);
        }
    }

    @Transactional
    public ProductDetailResponse publishProduct(SanguiPrincipal principal, Long productId) {
        requireAdmin(principal);
        ProductSnapshot snapshot = requireAdminSnapshot(principal.shopId(), productId);
        if (snapshot.product().status() != ProductStatus.DRAFT) {
            throw new SanguiException(ProductErrorCode.PRODUCT_STATUS_INVALID, 409);
        }
        productRepository.updateProductStatus(
                principal.shopId(),
                productId,
                principal.userId(),
                ProductStatus.ACTIVE
        );
        return getAdminProduct(principal.shopId(), productId);
    }

    private ProductSnapshot requireAdminSnapshot(Long shopId, Long productId) {
        return productRepository.findAdminProduct(shopId, productId)
                .orElseThrow(() -> new SanguiException(ProductErrorCode.PRODUCT_NOT_FOUND, 404));
    }

    private ProductDetailResponse getAdminProduct(Long shopId, Long productId) {
        return toDetailResponse(requireAdminSnapshot(shopId, productId));
    }

    private void requireAdmin(SanguiPrincipal principal) {
        if (principal.roles() == null || !principal.roles().contains(ADMIN_ROLE)) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
    }

    private ProductDraft toDraft(
            String productName,
            String productDescription,
            List<UpsertProductSkuRequest> skuRequests
    ) {
        rejectDuplicateSkuCodes(skuRequests);
        List<ProductSkuDraft> skus = skuRequests.stream()
                .map(sku -> new ProductSkuDraft(
                        sku.skuCode().trim(),
                        sku.skuName().trim(),
                        sku.priceCent(),
                        normalizeAvailableStock(sku.availableStock())
                ))
                .toList();
        return new ProductDraft(productName.trim(), trimToNull(productDescription), skus);
    }

    private void rejectDuplicateSkuCodes(List<UpsertProductSkuRequest> skuRequests) {
        Set<String> seenCodes = new HashSet<>();
        for (UpsertProductSkuRequest skuRequest : skuRequests) {
            String normalized = skuRequest.skuCode().trim().toLowerCase();
            if (!seenCodes.add(normalized)) {
                throw new SanguiException(ProductErrorCode.PRODUCT_SKU_CODE_EXISTS, 409);
            }
        }
    }

    private ProductDetailResponse toDetailResponse(ProductSnapshot snapshot) {
        ProductRecord product = snapshot.product();
        List<ProductSkuResponse> skus = snapshot.skus().stream()
                .map(sku -> new ProductSkuResponse(
                        sku.id(),
                        sku.skuCode(),
                        sku.skuName(),
                        sku.priceCent(),
                        sku.availableStock(),
                        sku.reservedStock()
                ))
                .collect(Collectors.toList());
        return new ProductDetailResponse(
                product.id(),
                product.productName(),
                product.productDescription(),
                product.status().value(),
                skus
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long normalizeAvailableStock(Long availableStock) {
        return availableStock == null ? 0L : availableStock;
    }
}
