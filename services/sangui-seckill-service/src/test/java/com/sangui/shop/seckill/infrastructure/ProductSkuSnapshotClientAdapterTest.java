package com.sangui.shop.seckill.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.seckill.domain.ProductSkuSnapshotClient.ProductSkuSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

class ProductSkuSnapshotClientAdapterTest {

    private ProductSkuSnapshotClientAdapter adapter;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9999");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        adapter = new ProductSkuSnapshotClientAdapter(restClient, objectMapper);
    }

    @Test
    void findBySkuIdReturnsSnapshotForValidSku() throws Exception {
        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("code", "PRODUCT_SKU_SNAPSHOTS_FETCHED");
        responseNode.put("message", "ok");
        ObjectNode dataNode = responseNode.putObject("data");
        ArrayNode itemsNode = dataNode.putArray("items");
        ObjectNode itemNode = itemsNode.addObject();
        itemNode.put("productId", 301);
        itemNode.put("productName", "Running Shoe");
        itemNode.put("skuId", 401);
        itemNode.put("skuCode", "RS-42");
        itemNode.put("skuName", "42");
        itemNode.put("priceCent", 59900);
        itemNode.put("availableStock", 20);

        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9999/internal/products/skus/snapshot"))
                .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.header("X-Trace-Id", "trace-sku"))
                .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(responseNode), MediaType.APPLICATION_JSON));

        Optional<ProductSkuSnapshot> result = adapter.findBySkuId(1L, 401L, "trace-sku");

        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo(301);
        assertThat(result.get().productName()).isEqualTo("Running Shoe");
        assertThat(result.get().skuId()).isEqualTo(401);
        assertThat(result.get().skuCode()).isEqualTo("RS-42");
        assertThat(result.get().skuName()).isEqualTo("42");
        assertThat(result.get().priceCent()).isEqualTo(59900);
        assertThat(result.get().availableStock()).isEqualTo(20);
        mockServer.verify();
    }

    @Test
    void findBySkuIdReturnsEmptyWhenSkuNotInResponse() throws Exception {
        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("code", "PRODUCT_SKU_SNAPSHOTS_FETCHED");
        responseNode.put("message", "ok");
        responseNode.putObject("data").putArray("items");

        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9999/internal/products/skus/snapshot"))
                .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(responseNode), MediaType.APPLICATION_JSON));

        Optional<ProductSkuSnapshot> result = adapter.findBySkuId(1L, 999L, "trace-sku");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void findBySkuIdThrowsDownstreamTimeoutOnServerError() throws Exception {
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("code", "INTERNAL_ERROR");
        errorNode.put("message", "error");

        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9999/internal/products/skus/snapshot"))
                .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(errorNode)));

        assertThatThrownBy(() -> adapter.findBySkuId(1L, 401L, "trace-sku"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.DOWNSTREAM_TIMEOUT.code());
                    assertThat(e.httpStatus()).isEqualTo(503);
                });
    }

    @Test
    void findBySkuIdThrowsDownstreamTimeoutOnEmptyResponse() {
        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:9999/internal/products/skus/snapshot"))
                .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess());

        assertThatThrownBy(() -> adapter.findBySkuId(1L, 401L, "trace-sku"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.DOWNSTREAM_TIMEOUT.code());
                    assertThat(e.httpStatus()).isEqualTo(503);
                });
    }

    @Test
    void findBySkuIdThrowsDownstreamTimeoutOnConnectionError() {
        RestClient.Builder badBuilder = RestClient.builder().baseUrl("http://localhost:1");
        RestClient badClient = badBuilder.build();
        ProductSkuSnapshotClientAdapter badAdapter = new ProductSkuSnapshotClientAdapter(badClient, objectMapper);

        assertThatThrownBy(() -> badAdapter.findBySkuId(1L, 401L, "trace-sku"))
                .isInstanceOfSatisfying(SanguiException.class, e -> {
                    assertThat(e.errorCode().code()).isEqualTo(CommonErrorCode.DOWNSTREAM_TIMEOUT.code());
                    assertThat(e.httpStatus()).isEqualTo(503);
                });
    }
}
