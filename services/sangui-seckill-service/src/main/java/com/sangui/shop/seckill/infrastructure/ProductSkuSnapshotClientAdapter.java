package com.sangui.shop.seckill.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient;
import com.sangui.shop.seckill.domain.SeckillErrorCode;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductSkuSnapshotClientAdapter implements ProductSkuSnapshotClient {

    private static final ParameterizedTypeReference<ApiResult<ProductSnapshotResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductSkuSnapshotClientAdapter(
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

    ProductSkuSnapshotClientAdapter(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProductSkuSnapshot> findBySkuId(Long shopId, Long skuId, String traceId) {
        try {
            ApiResult<ProductSnapshotResponse> result = restClient.post()
                    .uri("/internal/products/skus/snapshot")
                    .header(TraceConstants.TRACE_ID_HEADER, traceId == null ? "" : traceId)
                    .body(new ProductSnapshotRequest(shopId, List.of(skuId)))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw mapSanguiException(response.getStatusCode(), response.getBody());
                    })
                    .body(RESPONSE_TYPE);
            return mapFirstMatch(result, skuId);
        } catch (RestClientException exception) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
    }

    private Optional<ProductSkuSnapshot> mapFirstMatch(ApiResult<ProductSnapshotResponse> result, Long targetSkuId) {
        if (result == null || result.data() == null || result.data().items() == null) {
            throw new SanguiException(CommonErrorCode.DOWNSTREAM_TIMEOUT, 503);
        }
        return result.data().items().stream()
                .filter(item -> item.skuId().equals(targetSkuId))
                .findFirst()
                .map(item -> new ProductSkuSnapshot(
                        item.productId(),
                        item.productName(),
                        item.skuId(),
                        item.skuCode(),
                        item.skuName(),
                        item.priceCent(),
                        item.availableStock()
                ));
    }

    private SanguiException mapSanguiException(HttpStatusCode statusCode, java.io.InputStream bodyStream) {
        try {
            if (bodyStream != null) {
                JsonNode root = objectMapper.readTree(bodyStream);
                String code = readText(root, "code");
                String message = readText(root, "message");
                int status = statusCode.value();
                if (status == 404 && "PRODUCT_SKU_NOT_FOUND".equals(code)) {
                    return new SanguiException(SeckillErrorCode.PRODUCT_SKU_NOT_FOUND, message, 404);
                }
                if (status == 409 && "PRODUCT_STOCK_NOT_ENOUGH".equals(code)) {
                    return new SanguiException(SeckillErrorCode.PRODUCT_STOCK_NOT_ENOUGH, message, 409);
                }
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

    private record ProductSnapshotRequest(Long shopId, List<Long> skuIds) {
    }

    private record ProductSnapshotResponse(List<ProductSnapshotItem> items) {
    }

    private record ProductSnapshotItem(
            Long productId,
            String productName,
            Long skuId,
            String skuCode,
            String skuName,
            Long priceCent,
            Long availableStock
    ) {
    }
}
