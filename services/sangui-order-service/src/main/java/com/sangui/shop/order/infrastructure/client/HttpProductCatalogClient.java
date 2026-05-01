package com.sangui.shop.order.infrastructure.client;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.client.ProductSkuSnapshot;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpProductCatalogClient implements ProductCatalogClient {

    private static final ParameterizedTypeReference<ApiResult<ProductSkuSnapshotResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public HttpProductCatalogClient(
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
    public List<ProductSkuSnapshot> listActiveSkuSnapshots(Long shopId, List<Long> skuIds) {
        try {
            ApiResult<ProductSkuSnapshotResponse> result = restClient.post()
                    .uri("/internal/products/skus/snapshot")
                    .body(new ProductSkuSnapshotRequest(shopId, skuIds))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (result == null || result.data() == null || result.data().items() == null) {
                throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
            }
            return result.data().items().stream()
                    .map(this::toSnapshot)
                    .toList();
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }

    private ProductSkuSnapshot toSnapshot(ProductSkuSnapshotItemResponse item) {
        return new ProductSkuSnapshot(
                item.productId(),
                item.skuId(),
                item.skuCode(),
                item.skuName(),
                item.priceCent()
        );
    }
}
