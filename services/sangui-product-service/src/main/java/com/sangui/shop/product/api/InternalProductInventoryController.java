package com.sangui.shop.product.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.product.application.ProductInventoryService;
import com.sangui.shop.product.client.dto.InventoryConfirmRequest;
import com.sangui.shop.product.client.dto.InventoryReleaseRequest;
import com.sangui.shop.product.client.dto.InventoryReservationItemResponse;
import com.sangui.shop.product.client.dto.InventoryReservationResponse;
import com.sangui.shop.product.client.dto.InventoryReserveRequest;
import com.sangui.shop.product.domain.ProductInventoryReservationSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products/inventory")
public class InternalProductInventoryController {

    private final ProductInventoryService productInventoryService;

    public InternalProductInventoryController(ProductInventoryService productInventoryService) {
        this.productInventoryService = productInventoryService;
    }

    @PostMapping("/reservations")
    public ApiResult<InventoryReservationResponse> reserve(
            @Valid @RequestBody InventoryReserveRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductInventoryReservationSnapshot snapshot = productInventoryService.reserve(request, traceId(httpRequest));
        return ApiResult.ok("PRODUCT_INVENTORY_RESERVED", toResponse(snapshot), traceId(httpRequest));
    }

    @PostMapping("/confirmations")
    public ApiResult<InventoryReservationResponse> confirm(
            @Valid @RequestBody InventoryConfirmRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductInventoryReservationSnapshot snapshot = productInventoryService.confirm(
                request.shopId(),
                request.reservationNo().trim(),
                traceId(httpRequest)
        );
        return ApiResult.ok("PRODUCT_INVENTORY_CONFIRMED", toResponse(snapshot), traceId(httpRequest));
    }

    @PostMapping("/releases")
    public ApiResult<InventoryReservationResponse> release(
            @Valid @RequestBody InventoryReleaseRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductInventoryReservationSnapshot snapshot = productInventoryService.release(
                request.shopId(),
                request.reservationNo().trim(),
                traceId(httpRequest)
        );
        return ApiResult.ok("PRODUCT_INVENTORY_RELEASED", toResponse(snapshot), traceId(httpRequest));
    }

    private InventoryReservationResponse toResponse(ProductInventoryReservationSnapshot snapshot) {
        return new InventoryReservationResponse(
                snapshot.reservationNo(),
                snapshot.status().value(),
                snapshot.items().stream()
                        .map(item -> new InventoryReservationItemResponse(
                                item.productId(),
                                item.skuId(),
                                item.skuCode(),
                                item.skuName(),
                                item.priceCent(),
                                item.quantity()
                        ))
                        .toList()
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
