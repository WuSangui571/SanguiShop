package com.sangui.shop.product.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.api.PageRequest;
import com.sangui.shop.common.core.api.PageResponse;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.product.api.dto.CreateProductRequest;
import com.sangui.shop.product.api.dto.ProductAdminSummaryResponse;
import com.sangui.shop.product.api.dto.ProductDetailResponse;
import com.sangui.shop.product.api.dto.ProductReviewPageResponse;
import com.sangui.shop.product.api.dto.ProductSkuStockAdjustmentRequest;
import com.sangui.shop.product.api.dto.ProductSummaryResponse;
import com.sangui.shop.product.api.dto.ProductStatusUpdateRequest;
import com.sangui.shop.product.api.dto.UpdateProductRequest;
import com.sangui.shop.product.application.ProductCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;

    public ProductCatalogController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping("/products")
    public ApiResult<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest
    ) {
        PageResponse<ProductSummaryResponse> response = productCatalogService.listProducts(new PageRequest(page, size));
        return ApiResult.ok("PRODUCT_LISTED", response, traceId(httpRequest));
    }

    @GetMapping("/products/{productId}")
    public ApiResult<ProductDetailResponse> getProduct(
            @PathVariable @Positive Long productId,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.getProduct(productId);
        return ApiResult.ok("PRODUCT_FETCHED", response, traceId(httpRequest));
    }

    @GetMapping("/products/{productId}/reviews")
    public ApiResult<ProductReviewPageResponse> listProductReviews(
            @PathVariable @Positive Long productId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "false") boolean withImages,
            HttpServletRequest httpRequest
    ) {
        String traceId = traceId(httpRequest);
        ProductReviewPageResponse response = productCatalogService.listProductReviews(
                productId,
                new PageRequest(page, size),
                withImages,
                traceId
        );
        return ApiResult.ok("PRODUCT_REVIEWS_FETCHED", response, traceId);
    }

    @GetMapping("/admin/products")
    public ApiResult<PageResponse<ProductAdminSummaryResponse>> listAdminProducts(
            SanguiPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            HttpServletRequest httpRequest
    ) {
        PageResponse<ProductAdminSummaryResponse> response = productCatalogService.listAdminProducts(
                principal,
                new PageRequest(page, size),
                status
        );
        return ApiResult.ok("PRODUCT_ADMIN_LISTED", response, traceId(httpRequest));
    }

    @GetMapping("/admin/products/{productId}")
    public ApiResult<ProductDetailResponse> getAdminProduct(
            SanguiPrincipal principal,
            @PathVariable @Positive Long productId,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.getAdminProduct(principal, productId);
        return ApiResult.ok("PRODUCT_ADMIN_FETCHED", response, traceId(httpRequest));
    }

    @PostMapping("/admin/products")
    public ApiResult<ProductDetailResponse> createProduct(
            SanguiPrincipal principal,
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.createProduct(principal, request);
        return ApiResult.ok("PRODUCT_CREATED", response, traceId(httpRequest));
    }

    @PutMapping("/admin/products/{productId}")
    public ApiResult<ProductDetailResponse> updateProduct(
            SanguiPrincipal principal,
            @PathVariable @Positive Long productId,
            @Valid @RequestBody UpdateProductRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.updateProduct(principal, productId, request);
        return ApiResult.ok("PRODUCT_UPDATED", response, traceId(httpRequest));
    }

    @PostMapping("/admin/products/{productId}/publish")
    public ApiResult<ProductDetailResponse> publishProduct(
            SanguiPrincipal principal,
            @PathVariable @Positive Long productId,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.publishProduct(principal, productId);
        return ApiResult.ok("PRODUCT_PUBLISHED", response, traceId(httpRequest));
    }

    @PostMapping("/admin/products/{productId}/status")
    public ApiResult<ProductDetailResponse> updateProductStatus(
            SanguiPrincipal principal,
            @PathVariable @Positive Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.updateProductStatus(principal, productId, request);
        return ApiResult.ok("PRODUCT_STATUS_UPDATED", response, traceId(httpRequest));
    }

    @PostMapping("/admin/products/{productId}/skus/{skuId}/stock-adjustments")
    public ApiResult<ProductDetailResponse> adjustSkuStock(
            SanguiPrincipal principal,
            @PathVariable @Positive Long productId,
            @PathVariable @Positive Long skuId,
            @Valid @RequestBody ProductSkuStockAdjustmentRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductDetailResponse response = productCatalogService.adjustSkuStock(principal, productId, skuId, request);
        return ApiResult.ok("PRODUCT_SKU_STOCK_ADJUSTED", response, traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
