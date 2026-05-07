package com.sangui.shop.logistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminFulfillmentServiceTest {

    private FakeOrderFulfillmentClient orderClient;
    private InMemoryShipmentRepository shipmentRepository;
    private AdminFulfillmentService service;

    @BeforeEach
    void setUp() {
        orderClient = new FakeOrderFulfillmentClient();
        shipmentRepository = new InMemoryShipmentRepository();
        service = new AdminFulfillmentService(orderClient, shipmentRepository);
    }

    @Test
    void shipFulfillmentConfirmsOrderAndStoresShipment() {
        FulfillmentOrderResponse response = service.shipFulfillment(
                fulfillmentPrincipal(),
                101L,
                new ShipFulfillmentRequest(" ship-001 ", " SF Express ", " SF123 "),
                "trace-ship"
        );

        assertThat(response.fulfillmentStatus()).isEqualTo("shipped");
        assertThat(orderClient.lastConfirmRequest.requestId()).isEqualTo("ship-001");
        ShipmentRecord shipment = shipmentRepository.findByOrderId(1L, 101L).orElseThrow();
        assertThat(shipment.carrier()).isEqualTo("SF Express");
        assertThat(shipment.trackingNo()).isEqualTo("SF123");
        assertThat(shipment.traceId()).isEqualTo("trace-ship");
    }

    @Test
    void duplicateRequestWithDifferentPayloadFails() {
        service.shipFulfillment(fulfillmentPrincipal(), 101L, new ShipFulfillmentRequest("ship-001", "SF Express", "SF123"), "trace-ship");

        assertThatThrownBy(() -> service.shipFulfillment(
                fulfillmentPrincipal(),
                101L,
                new ShipFulfillmentRequest("ship-001", "YTO", "YT999"),
                "trace-ship-2"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(CommonErrorCode.IDEMPOTENCY_CONFLICT.code());
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void compensationPermissionAloneCannotShip() {
        SanguiPrincipal principal = new SanguiPrincipal("ops", 1L, Set.of(), Set.of(SanguiPermissionConstants.OPS_COMPENSATION_ADMIN), "jwt");

        assertThatThrownBy(() -> service.shipFulfillment(
                principal,
                101L,
                new ShipFulfillmentRequest("ship-001", "SF Express", "SF123"),
                "trace-ship"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo(CommonErrorCode.AUTH_FORBIDDEN.code());
            assertThat(exception.httpStatus()).isEqualTo(403);
        });
    }

    private SanguiPrincipal fulfillmentPrincipal() {
        return new SanguiPrincipal("90001", 1L, Set.of(), Set.of(SanguiPermissionConstants.LOGISTICS_FULFILLMENT_ADMIN), "jwt");
    }

    private static final class FakeOrderFulfillmentClient implements OrderFulfillmentClient {
        private ConfirmOrderShipmentRequest lastConfirmRequest;

        @Override
        public FulfillmentOrderPageResponse queryFulfillments(FulfillmentOrderQueryRequest request, String traceId) {
            return new FulfillmentOrderPageResponse(1, 20, 0, List.of());
        }

        @Override
        public FulfillmentOrderResponse getFulfillment(FulfillmentOrderDetailRequest request, String traceId) {
            return response(request.orderId(), "paid", "unshipped", null, null);
        }

        @Override
        public FulfillmentOrderResponse confirmShipment(ConfirmOrderShipmentRequest request, String traceId) {
            lastConfirmRequest = request;
            return response(request.orderId(), "shipped", "shipped", request.carrier().trim(), request.trackingNo().trim());
        }

        private FulfillmentOrderResponse response(Long orderId, String status, String fulfillmentStatus, String carrier, String trackingNo) {
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-07T10:00:00+08:00");
            return new FulfillmentOrderResponse(
                    orderId,
                    "ORD-" + orderId,
                    1L,
                    "10001",
                    status,
                    fulfillmentStatus,
                    59900L,
                    carrier,
                    trackingNo,
                    "shipped".equals(fulfillmentStatus) ? timestamp : null,
                    "trace-order",
                    timestamp,
                    timestamp
            );
        }
    }

    private static final class InMemoryShipmentRepository implements ShipmentRepository {
        private final AtomicLong nextId = new AtomicLong(1000);
        private final Map<Long, ShipmentRecord> byOrderId = new LinkedHashMap<>();
        private final Map<String, ShipmentRecord> byRequestId = new LinkedHashMap<>();

        @Override
        public Optional<ShipmentRecord> findByOrderId(Long shopId, Long orderId) {
            ShipmentRecord record = byOrderId.get(orderId);
            return record == null || !java.util.Objects.equals(record.shopId(), shopId) ? Optional.empty() : Optional.of(record);
        }

        @Override
        public Optional<ShipmentRecord> findByRequestId(Long shopId, String requestId) {
            ShipmentRecord record = byRequestId.get(requestId);
            return record == null || !java.util.Objects.equals(record.shopId(), shopId) ? Optional.empty() : Optional.of(record);
        }

        @Override
        public Long create(ShipmentRecord record) {
            Long id = nextId.incrementAndGet();
            ShipmentRecord stored = new ShipmentRecord(
                    id,
                    record.shopId(),
                    record.orderId(),
                    record.orderNo(),
                    record.userId(),
                    record.carrier(),
                    record.trackingNo(),
                    record.status(),
                    record.requestId(),
                    record.traceId(),
                    record.createdAt(),
                    record.updatedAt()
            );
            byOrderId.put(stored.orderId(), stored);
            byRequestId.put(stored.requestId(), stored);
            return id;
        }
    }
}
