package com.sangui.shop.product.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.product.client.dto.InventoryReserveItemRequest;
import com.sangui.shop.product.client.dto.InventoryReserveRequest;
import com.sangui.shop.product.domain.ProductErrorCode;
import com.sangui.shop.product.domain.ProductInventoryReservationItemDraft;
import com.sangui.shop.product.domain.ProductInventoryReservationRecord;
import com.sangui.shop.product.domain.ProductInventoryReservationSnapshot;
import com.sangui.shop.product.domain.ProductInventoryReservationStatus;
import com.sangui.shop.product.domain.ProductRepository;
import com.sangui.shop.product.domain.ProductSkuRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductInventoryService {

    private final ProductRepository productRepository;

    public ProductInventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductInventoryReservationSnapshot reserve(InventoryReserveRequest request, String traceId) {
        String reservationNo = request.reservationNo().trim();
        List<ProductInventoryReservationRecord> existing = productRepository.findReservationRecords(request.shopId(), reservationNo);
        if (!existing.isEmpty()) {
            return ensureReplay(existing, request);
        }

        rejectDuplicateSkuIds(request.items());
        List<ProductInventoryReservationItemDraft> requestedItems = request.items().stream()
                .map(item -> new ProductInventoryReservationItemDraft(item.skuId(), item.quantity()))
                .toList();

        List<Long> skuIds = requestedItems.stream()
                .map(ProductInventoryReservationItemDraft::skuId)
                .toList();
        Map<Long, ProductSkuRecord> skuById = productRepository.findActiveSkus(request.shopId(), skuIds).stream()
                .collect(Collectors.toMap(ProductSkuRecord::id, sku -> sku, (left, right) -> left, LinkedHashMap::new));
        for (ProductInventoryReservationItemDraft item : requestedItems) {
            ProductSkuRecord sku = skuById.get(item.skuId());
            if (sku == null) {
                throw new SanguiException(ProductErrorCode.PRODUCT_SKU_NOT_FOUND, 404);
            }
            if (productRepository.reserveSkuStock(request.shopId(), item.skuId(), item.quantity()) == 0) {
                throw new SanguiException(ProductErrorCode.PRODUCT_STOCK_NOT_ENOUGH, 409);
            }
        }

        List<ProductInventoryReservationRecord> reservationRecords = requestedItems.stream()
                .map(item -> {
                    ProductSkuRecord sku = skuById.get(item.skuId());
                    return new ProductInventoryReservationRecord(
                            request.shopId(),
                            reservationNo,
                            sku.productId(),
                            sku.id(),
                            sku.skuCode(),
                            sku.skuName(),
                            sku.priceCent(),
                            item.quantity(),
                            ProductInventoryReservationStatus.RESERVED,
                            normalizeTraceId(traceId)
                    );
                })
                .toList();
        try {
            productRepository.createReservationRecords(
                    request.shopId(),
                    reservationNo,
                    reservationRecords,
                    ProductInventoryReservationStatus.RESERVED,
                    normalizeTraceId(traceId)
            );
        } catch (DuplicateKeyException exception) {
            List<ProductInventoryReservationRecord> duplicated = productRepository.findReservationRecords(request.shopId(), reservationNo);
            if (!duplicated.isEmpty()) {
                return ensureReplay(duplicated, request);
            }
            throw exception;
        }
        return new ProductInventoryReservationSnapshot(
                request.shopId(),
                reservationNo,
                ProductInventoryReservationStatus.RESERVED,
                reservationRecords
        );
    }

    @Transactional
    public ProductInventoryReservationSnapshot confirm(Long shopId, String reservationNo, String traceId) {
        return transition(shopId, reservationNo, ProductInventoryReservationStatus.CONFIRMED, normalizeTraceId(traceId));
    }

    @Transactional
    public ProductInventoryReservationSnapshot release(Long shopId, String reservationNo, String traceId) {
        return transition(shopId, reservationNo, ProductInventoryReservationStatus.RELEASED, normalizeTraceId(traceId));
    }

    private ProductInventoryReservationSnapshot ensureReplay(
            List<ProductInventoryReservationRecord> existing,
            InventoryReserveRequest request
    ) {
        if (!matchesRequest(existing, request.items())) {
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }
        ProductInventoryReservationStatus status = existing.getFirst().status();
        if (status == ProductInventoryReservationStatus.RELEASED) {
            throw new SanguiException(ProductErrorCode.PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID, 409);
        }
        return new ProductInventoryReservationSnapshot(request.shopId(), request.reservationNo().trim(), status, existing);
    }

    private ProductInventoryReservationSnapshot transition(
            Long shopId,
            String reservationNo,
            ProductInventoryReservationStatus targetStatus,
            String traceId
    ) {
        List<ProductInventoryReservationRecord> existing = productRepository.findReservationRecords(shopId, reservationNo);
        if (existing.isEmpty()) {
            throw new SanguiException(ProductErrorCode.PRODUCT_INVENTORY_RESERVATION_NOT_FOUND, 404);
        }
        ProductInventoryReservationStatus currentStatus = existing.getFirst().status();
        if (currentStatus == targetStatus) {
            return new ProductInventoryReservationSnapshot(shopId, reservationNo, targetStatus, existing);
        }
        if (currentStatus != ProductInventoryReservationStatus.RESERVED) {
            throw new SanguiException(ProductErrorCode.PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID, 409);
        }

        int updated = productRepository.updateReservationStatus(
                shopId,
                reservationNo,
                ProductInventoryReservationStatus.RESERVED,
                targetStatus,
                traceId
        );
        if (updated == existing.size()) {
            for (ProductInventoryReservationRecord record : existing) {
                int stockUpdated = targetStatus == ProductInventoryReservationStatus.CONFIRMED
                        ? productRepository.confirmReservedSkuStock(shopId, record.skuId(), record.quantity())
                        : productRepository.releaseReservedSkuStock(shopId, record.skuId(), record.quantity());
                if (stockUpdated == 0) {
                    throw new SanguiException(ProductErrorCode.PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID, 409);
                }
            }
            List<ProductInventoryReservationRecord> transitioned = existing.stream()
                    .map(item -> item.withStatus(targetStatus, traceId))
                    .toList();
            return new ProductInventoryReservationSnapshot(shopId, reservationNo, targetStatus, transitioned);
        }

        List<ProductInventoryReservationRecord> latest = productRepository.findReservationRecords(shopId, reservationNo);
        ProductInventoryReservationStatus latestStatus = latest.getFirst().status();
        if (latestStatus == targetStatus) {
            return new ProductInventoryReservationSnapshot(shopId, reservationNo, latestStatus, latest);
        }
        throw new SanguiException(ProductErrorCode.PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID, 409);
    }

    private boolean matchesRequest(
            List<ProductInventoryReservationRecord> existing,
            List<InventoryReserveItemRequest> requestItems
    ) {
        if (existing.size() != requestItems.size()) {
            return false;
        }
        Map<Long, Integer> quantitiesBySkuId = existing.stream()
                .collect(Collectors.toMap(ProductInventoryReservationRecord::skuId, ProductInventoryReservationRecord::quantity));
        for (InventoryReserveItemRequest requestItem : requestItems) {
            if (!Objects.equals(quantitiesBySkuId.get(requestItem.skuId()), requestItem.quantity())) {
                return false;
            }
        }
        return true;
    }

    private void rejectDuplicateSkuIds(List<InventoryReserveItemRequest> items) {
        Set<Long> seenSkuIds = new java.util.HashSet<>();
        for (InventoryReserveItemRequest item : items) {
            if (!seenSkuIds.add(item.skuId())) {
                throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
            }
        }
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String trimmed = traceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
