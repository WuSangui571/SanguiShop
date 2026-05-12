package com.sangui.shop.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.product.application.ProductCatalogService;
import com.sangui.shop.product.domain.ProductSkuRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalProductSnapshotController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalProductSnapshotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductCatalogService productCatalogService;

    @Test
    void listSkuSnapshotsReturnsStableEnvelope() throws Exception {
        org.mockito.Mockito.when(productCatalogService.listActiveSkuSnapshots(eq(1L), any()))
                .thenReturn(List.of(
                        new ProductSkuRecord(401L, 301L, "Running Shoe", "shoe-42", "Sneaker 42", 59900L, 20L, 1L)
                ));

        mockMvc.perform(post("/internal/products/skus/snapshot")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "skuIds", List.of(401, 402)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_SKU_SNAPSHOTS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-snapshot"))
                .andExpect(jsonPath("$.data.items[0].productId").value(301))
                .andExpect(jsonPath("$.data.items[0].productName").value("Running Shoe"))
                .andExpect(jsonPath("$.data.items[0].skuId").value(401))
                .andExpect(jsonPath("$.data.items[0].skuCode").value("shoe-42"))
                .andExpect(jsonPath("$.data.items[0].skuName").value("Sneaker 42"))
                .andExpect(jsonPath("$.data.items[0].priceCent").value(59900))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(20));
    }

    @Test
    void listSkuSnapshotsValidatesRequestBody() throws Exception {
        mockMvc.perform(post("/internal/products/skus/snapshot")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-snapshot-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 0,
                                "skuIds", List.of()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-snapshot-validation"));
    }
}
