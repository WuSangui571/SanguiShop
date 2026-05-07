package com.sangui.shop.logistics.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPermissionConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.logistics.api.dto.ShipFulfillmentRequest;
import com.sangui.shop.logistics.client.ConfirmOrderShipmentRequest;
import com.sangui.shop.logistics.client.FulfillmentOrderDetailRequest;
import com.sangui.shop.logistics.client.FulfillmentOrderPageResponse;
import com.sangui.shop.logistics.client.FulfillmentOrderQueryRequest;
import com.sangui.shop.logistics.client.FulfillmentOrderResponse;
import com.sangui.shop.logistics.client.OrderFulfillmentClient;
import com.sangui.shop.logistics.domain.ShipmentRecord;
import com.sangui.shop.logistics.domain.ShipmentRepository;
import com.sangui.shop.logistics.domain.ShipmentStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminFulfillmentService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final OrderFulfillmentClient orderFulfillmentClient;
    private final ShipmentRepository shipmentRepository;

    public AdminFulfillmentService(OrderFulfillmentClient orderFulfillmentClient, ShipmentRepository shipmentRepository) {
        this.orderFulfillmentClient = orderFulfillmentClient;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional(readOnly = true)
    public FulfillmentOrderPageResponse listFulfillments(
            SanguiPrincipal principal,
            int page,
            int size,
            String status,
            String orderNo,
            String userId,
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            String traceId
    ) {
        requireAdmin(principal);
        return orderFulfillmentClient.queryFulfillments(
                new FulfillmentOrderQueryRequest(principal.shopId(), page, size, status, orderNo, userId, fromTime, toTime),
                traceId
        );
    }

    @Transactional(readOnly = true)
    public FulfillmentOrderResponse getFulfillment(SanguiPrincipal principal, Long orderId, String traceId) {
        requireAdmin(principal);
        return orderFulfillmentClient.getFulfillment(new FulfillmentOrderDetailRequest(principal.shopId(), orderId), traceId);
    }

    @Transactional
    public FulfillmentOrderResponse shipFulfillment(
            SanguiPrincipal principal,
            Long orderId,
            ShipFulfillmentRequest request,
            String traceId
    ) {
        requireAdmin(principal);
        String requestId = requireText(request.requestId());
        String carrier = requireText(request.carrier());
        String trackingNo = requireText(request.trackingNo());
        shipmentRepository.findByRequestId(principal.shopId(), requestId).ifPresent(existing -> {
            if (!samePayload(existing, orderId, carrier, trackingNo)) {
                throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
            }
        });
        shipmentRepository.findByOrderId(principal.shopId(), orderId).ifPresent(existing -> {
            if (!samePayload(existing, orderId, carrier, trackingNo) || !Objects.equals(existing.requestId(), requestId)) {
                throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
            }
        });
        FulfillmentOrderResponse response = orderFulfillmentClient.confirmShipment(
                new ConfirmOrderShipmentRequest(principal.shopId(), orderId, requestId, carrier, trackingNo),
                traceId
        );
        if (shipmentRepository.findByOrderId(principal.shopId(), orderId).isEmpty()) {
            shipmentRepository.create(new ShipmentRecord(
                    null,
                    principal.shopId(),
                    response.orderId(),
                    response.orderNo(),
                    response.userId(),
                    carrier,
                    trackingNo,
                    ShipmentStatus.SHIPPED,
                    requestId,
                    traceId,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            ));
        }
        return response;
    }

    private void requireAdmin(SanguiPrincipal principal) {
        boolean hasAdminRole = principal.roles() != null && principal.roles().contains(ADMIN_ROLE);
        boolean hasFulfillmentPermission = principal.permissions() != null
                && principal.permissions().contains(SanguiPermissionConstants.LOGISTICS_FULFILLMENT_ADMIN);
        if (!hasAdminRole && !hasFulfillmentPermission) {
            throw new SanguiException(CommonErrorCode.AUTH_FORBIDDEN, 403);
        }
    }

    private boolean samePayload(ShipmentRecord record, Long orderId, String carrier, String trackingNo) {
        return Objects.equals(record.orderId(), orderId)
                && Objects.equals(record.carrier(), carrier)
                && Objects.equals(record.trackingNo(), trackingNo);
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
