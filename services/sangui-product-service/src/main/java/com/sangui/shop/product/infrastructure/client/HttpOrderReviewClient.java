package com.sangui.shop.product.infrastructure.client;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.product.client.OrderReviewClient;
import com.sangui.shop.product.client.dto.ProductReviewPageResponse;
import com.sangui.shop.product.client.dto.ProductReviewQueryRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpOrderReviewClient implements OrderReviewClient {

    private static final ParameterizedTypeReference<ApiResult<ProductReviewPageResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public HttpOrderReviewClient(
            @Value("${sangui.client.order.base-url}") String baseUrl,
            @Value("${sangui.client.order.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${sangui.client.order.read-timeout-ms:3000}") int readTimeoutMs
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
    public ProductReviewPageResponse listProductReviews(
            Long shopId,
            Long productId,
            int page,
            int size,
            boolean withImages,
            String traceId
    ) {
        try {
            ApiResult<ProductReviewPageResponse> result = restClient.post()
                    .uri("/internal/orders/reviews/by-product/query")
                    .header("X-Trace-Id", traceId == null ? "" : traceId)
                    .body(new ProductReviewQueryRequest(shopId, productId, page, size, withImages))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
                    })
                    .body(RESPONSE_TYPE);
            if (result == null || result.data() == null) {
                throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
            }
            return result.data();
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }
}
