package com.sangui.shop.payment.infrastructure.client;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.payment.client.ProductInventoryClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpProductInventoryClient implements ProductInventoryClient {

    private static final ParameterizedTypeReference<ApiResult<InventoryReservationResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public HttpProductInventoryClient(
            @Value("${sangui.client.product.base-url}") String baseUrl,
            @Value("${sangui.client.product.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${sangui.client.product.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void confirmReservation(Long shopId, String reservationNo, String traceId) {
        try {
            ApiResult<InventoryReservationResponse> result = restClient.post()
                    .uri("/internal/products/inventory/confirmations")
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
                    .body(new InventoryConfirmRequest(shopId, reservationNo))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (result == null || result.data() == null) {
                throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
            }
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }
}
