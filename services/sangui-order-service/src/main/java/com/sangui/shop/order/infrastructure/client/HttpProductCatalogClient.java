package com.sangui.shop.order.infrastructure.client;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.InventoryReservationItemSnapshot;
import com.sangui.shop.order.client.InventoryReserveItemSnapshot;
import com.sangui.shop.order.client.InventoryReservationSnapshot;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.domain.OrderErrorCode;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpProductCatalogClient implements ProductCatalogClient {

    private static final ParameterizedTypeReference<ApiResult<InventoryReservationResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpProductCatalogClient(
            @Value("${sangui.client.product.base-url}") String baseUrl,
            @Value("${sangui.client.product.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${sangui.client.product.read-timeout-ms:3000}") int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public InventoryReservationSnapshot reserveInventory(
            Long shopId,
            String reservationNo,
            List<InventoryReserveItemSnapshot> items,
            String traceId
    ) {
        try {
            ApiResult<InventoryReservationResponse> result = restClient.post()
                    .uri("/internal/products/inventory/reservations")
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
                    .body(new InventoryReserveRequest(
                            shopId,
                            reservationNo,
                            items.stream()
                                    .map(item -> new InventoryReserveItemRequest(item.skuId(), item.quantity()))
                                    .toList()
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw mapError(response.getStatusCode(), response.getBody());
                    })
                    .body(RESPONSE_TYPE);
            return toSnapshot(result);
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }

    @Override
    public InventoryReservationSnapshot releaseInventory(Long shopId, String reservationNo, String traceId) {
        try {
            ApiResult<InventoryReservationResponse> result = restClient.post()
                    .uri("/internal/products/inventory/releases")
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
                    .body(new InventoryReleaseRequest(shopId, reservationNo))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw mapError(response.getStatusCode(), response.getBody());
                    })
                    .body(RESPONSE_TYPE);
            return toSnapshot(result);
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }

    private InventoryReservationSnapshot toSnapshot(ApiResult<InventoryReservationResponse> result) {
        if (result == null || result.data() == null || result.data().items() == null) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
        return new InventoryReservationSnapshot(
                result.data().reservationNo(),
                result.data().status(),
                result.data().items().stream()
                        .map(item -> new InventoryReservationItemSnapshot(
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

    private SanguiException mapError(HttpStatusCode statusCode, java.io.InputStream bodyStream) {
        try {
            JsonNode root = objectMapper.readTree(bodyStream);
            String code = readText(root, "code");
            String message = readText(root, "message");
            int status = statusCode.value();
            if (status == 404 && "PRODUCT_SKU_NOT_FOUND".equals(code)) {
                return new SanguiException(OrderErrorCode.ORDER_SKU_NOT_FOUND, message, 404);
            }
            if (status == 409 && "PRODUCT_STOCK_NOT_ENOUGH".equals(code)) {
                return new SanguiException(OrderErrorCode.ORDER_STOCK_NOT_ENOUGH, message, 409);
            }
            if (status == 409 && "PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID".equals(code)) {
                return new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, message, 409);
            }
        } catch (IOException exception) {
            return new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
        return new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
    }

    private String readText(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }
}
