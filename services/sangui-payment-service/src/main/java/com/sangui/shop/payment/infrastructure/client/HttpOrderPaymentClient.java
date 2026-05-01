package com.sangui.shop.payment.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.client.OrderPaymentClient;
import com.sangui.shop.payment.client.OrderPaymentSnapshot;
import com.sangui.shop.payment.domain.PaymentErrorCode;
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
public class HttpOrderPaymentClient implements OrderPaymentClient {

    private static final ParameterizedTypeReference<ApiResult<OrderPaymentSnapshotResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpOrderPaymentClient(
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
    public OrderPaymentSnapshot getPayableOrder(Long shopId, String userId, Long orderId) {
        try {
            ApiResult<OrderPaymentSnapshotResponse> result = restClient.post()
                    .uri("/internal/orders/payment-snapshot")
                    .body(new OrderPaymentSnapshotRequest(shopId, userId, orderId))
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
    public OrderPaymentSnapshot confirmPaid(
            Long shopId,
            String userId,
            Long orderId,
            String paymentNo,
            Long paidAmountCent,
            String traceId
    ) {
        try {
            ApiResult<OrderPaymentSnapshotResponse> result = restClient.post()
                    .uri("/internal/orders/payment-confirmations")
                    .body(new ConfirmOrderPaymentRequest(shopId, userId, orderId, paymentNo, paidAmountCent))
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
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

    private OrderPaymentSnapshot toSnapshot(ApiResult<OrderPaymentSnapshotResponse> result) {
        if (result == null || result.data() == null) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
        OrderPaymentSnapshotResponse data = result.data();
        return new OrderPaymentSnapshot(
                data.orderId(),
                data.orderNo(),
                data.shopId(),
                data.userId(),
                data.status(),
                data.totalAmountCent()
        );
    }

    private SanguiException mapError(HttpStatusCode statusCode, java.io.InputStream bodyStream) {
        try {
            JsonNode root = objectMapper.readTree(bodyStream);
            String code = readText(root, "code");
            String message = readText(root, "message");
            int status = statusCode.value();
            if (status == 404 && "ORDER_NOT_FOUND".equals(code)) {
                return new SanguiException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND, message, 404);
            }
            if (status == 409 && "ORDER_STATUS_INVALID".equals(code)) {
                return new SanguiException(PaymentErrorCode.PAYMENT_ORDER_STATUS_INVALID, message, 409);
            }
            if (status == 409 && "ORDER_PAYMENT_AMOUNT_MISMATCH".equals(code)) {
                return new SanguiException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH, message, 409);
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
