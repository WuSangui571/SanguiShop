package com.sangui.shop.logistics.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.logistics.client.ConfirmOrderShipmentRequest;
import com.sangui.shop.logistics.client.FulfillmentOrderDetailRequest;
import com.sangui.shop.logistics.client.FulfillmentOrderPageResponse;
import com.sangui.shop.logistics.client.FulfillmentOrderQueryRequest;
import com.sangui.shop.logistics.client.FulfillmentOrderResponse;
import com.sangui.shop.logistics.client.OrderFulfillmentClient;
import com.sangui.shop.logistics.domain.LogisticsErrorCode;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpOrderFulfillmentClient implements OrderFulfillmentClient {

    private static final ParameterizedTypeReference<ApiResult<FulfillmentOrderPageResponse>> PAGE_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<FulfillmentOrderResponse>> DETAIL_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpOrderFulfillmentClient(
            @Value("${sangui.client.order.base-url}") String baseUrl,
            @Value("${sangui.client.order.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${sangui.client.order.read-timeout-ms:3000}") int readTimeoutMs,
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
    public FulfillmentOrderPageResponse queryFulfillments(FulfillmentOrderQueryRequest request, String traceId) {
        try {
            ApiResult<FulfillmentOrderPageResponse> result = restClient.post()
                    .uri("/internal/orders/fulfillment-records/query")
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        throw mapError(response.getStatusCode(), response.getBody());
                    })
                    .body(PAGE_TYPE);
            return result == null ? null : result.data();
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }

    @Override
    public FulfillmentOrderResponse getFulfillment(FulfillmentOrderDetailRequest request, String traceId) {
        return postDetail("/internal/orders/fulfillment-records/detail", request, traceId);
    }

    @Override
    public FulfillmentOrderResponse confirmShipment(ConfirmOrderShipmentRequest request, String traceId) {
        return postDetail("/internal/orders/shipments/confirmations", request, traceId);
    }

    private FulfillmentOrderResponse postDetail(String uri, Object request, String traceId) {
        try {
            ApiResult<FulfillmentOrderResponse> result = restClient.post()
                    .uri(uri)
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        throw mapError(response.getStatusCode(), response.getBody());
                    })
                    .body(DETAIL_TYPE);
            return result == null ? null : result.data();
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }

    private SanguiException mapError(HttpStatusCode statusCode, java.io.InputStream bodyStream) {
        try {
            JsonNode root = objectMapper.readTree(bodyStream);
            String code = readText(root, "code");
            String message = readText(root, "message");
            int status = statusCode.value();
            if (status == 404 && "ORDER_NOT_FOUND".equals(code)) {
                return new SanguiException(LogisticsErrorCode.ORDER_NOT_FOUND, message, 404);
            }
            if (status == 409 && "ORDER_STATUS_INVALID".equals(code)) {
                return new SanguiException(LogisticsErrorCode.ORDER_STATUS_INVALID, message, 409);
            }
            if (status == 409 && "IDEMPOTENCY_CONFLICT".equals(code)) {
                return new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, message, 409);
            }
        } catch (IOException exception) {
            return new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
        return new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
    }

    private String readText(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        return node == null || node.isNull() ? "" : node.asText("");
    }
}
