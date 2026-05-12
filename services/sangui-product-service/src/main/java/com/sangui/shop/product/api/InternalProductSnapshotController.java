package com.sangui.shop.product.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.product.application.ProductCatalogService;
import com.sangui.shop.product.client.dto.ProductSkuSnapshotItemResponse;
import com.sangui.shop.product.client.dto.ProductSkuSnapshotRequest;
import com.sangui.shop.product.client.dto.ProductSkuSnapshotResponse;
import com.sangui.shop.product.domain.ProductSkuRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
public class InternalProductSnapshotController {

    private final ProductCatalogService productCatalogService;

    public InternalProductSnapshotController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @PostMapping("/skus/snapshot")
    public ApiResult<ProductSkuSnapshotResponse> listSkuSnapshots(
            @Valid @RequestBody ProductSkuSnapshotRequest request,
            HttpServletRequest httpRequest
    ) {
        List<ProductSkuSnapshotItemResponse> items = productCatalogService.listActiveSkuSnapshots(
                        request.shopId(),
                        request.skuIds()
                ).stream()
                .map(this::toItemResponse)
                .toList();
        return ApiResult.ok("PRODUCT_SKU_SNAPSHOTS_FETCHED", new ProductSkuSnapshotResponse(items), traceId(httpRequest));
    }

    private ProductSkuSnapshotItemResponse toItemResponse(ProductSkuRecord sku) {
        return new ProductSkuSnapshotItemResponse(
                sku.productId(),
                sku.productName(),
                sku.id(),
                sku.skuCode(),
                sku.skuName(),
                sku.priceCent(),
                sku.availableStock()
        );
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
