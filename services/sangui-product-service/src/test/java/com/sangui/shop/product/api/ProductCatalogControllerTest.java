package com.sangui.shop.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.api.PageResponse;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.common.web.SanguiAuthenticationContextFilter;
import com.sangui.shop.common.web.SanguiPrincipalArgumentResolver;
import com.sangui.shop.product.api.dto.CreateProductRequest;
import com.sangui.shop.product.api.dto.ProductAdminSummaryResponse;
import com.sangui.shop.product.api.dto.ProductDetailResponse;
import com.sangui.shop.product.api.dto.ProductReviewItemResponse;
import com.sangui.shop.product.api.dto.ProductReviewPageResponse;
import com.sangui.shop.product.api.dto.ProductSkuResponse;
import com.sangui.shop.product.api.dto.ProductSkuStockAdjustmentRequest;
import com.sangui.shop.product.api.dto.ProductStatusUpdateRequest;
import com.sangui.shop.product.api.dto.ProductSummaryResponse;
import com.sangui.shop.product.application.ProductCatalogService;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductCatalogController.class)
@Import({GlobalApiExceptionHandler.class, ProductCatalogControllerTest.ResolverConfig.class})
class ProductCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductCatalogService productCatalogService;

    @Test
    void listProductsAllowsAnonymousBrowsing() throws Exception {
        when(productCatalogService.listProducts(any()))
                .thenReturn(new PageResponse<>(
                        List.of(new ProductSummaryResponse(101L, "Sneaker", "Daily trainer", 59900L, 69900L, "active")),
                        1L,
                        1,
                        20
                ));

        mockMvc.perform(get("/api/products")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_LISTED"))
                .andExpect(jsonPath("$.traceId").value("trace-products"))
                .andExpect(jsonPath("$.data.items[0].productId").value(101))
                .andExpect(jsonPath("$.data.items[0].status").value("active"));
    }

    @Test
    void getProductReturnsDetailEnvelope() throws Exception {
        when(productCatalogService.getProduct(101L))
                .thenReturn(new ProductDetailResponse(
                        101L,
                        "Sneaker",
                        "Daily trainer",
                        "active",
                        List.of(new ProductSkuResponse(201L, "shoe-42", "42", 59900L, 20L, 0L))
                ));

        mockMvc.perform(get("/api/products/101")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-detail"))
                .andExpect(jsonPath("$.data.productId").value(101))
                .andExpect(jsonPath("$.data.skus[0].skuCode").value("shoe-42"));
    }

    @Test
    void listProductReviewsReturnsPublicReviewEnvelope() throws Exception {
        when(productCatalogService.listProductReviews(eq(101L), any(), eq("trace-product-reviews")))
                .thenReturn(new ProductReviewPageResponse(
                        101L,
                        4.5,
                        2L,
                        1,
                        10,
                        List.of(new ProductReviewItemResponse(
                                9001L,
                                5,
                                "Matched expectations.",
                                List.of(),
                                OffsetDateTime.parse("2026-05-08T10:00:00+08:00"),
                                "10***01",
                                "Size 42"
                        ))
                ));

        mockMvc.perform(get("/api/products/101/reviews")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_REVIEWS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-reviews"))
                .andExpect(jsonPath("$.data.productId").value(101))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(9001))
                .andExpect(jsonPath("$.data.items[0].maskedUserId").value("10***01"))
                .andExpect(jsonPath("$.data.items[0].skuName").value("Size 42"))
                .andExpect(jsonPath("$.data.items[0].orderNo").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].traceId").doesNotExist());
    }

    @Test
    void listAdminProductsReturnsOverviewEnvelope() throws Exception {
        when(productCatalogService.listAdminProducts(any(), any(), any()))
                .thenReturn(new PageResponse<>(
                        List.of(new ProductAdminSummaryResponse(101L, "Sneaker", "Daily trainer", 59900L, 69900L, "active", 2L, 30L, 0L)),
                        1L,
                        1,
                        20
                ));

        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of(), java.util.Set.of(com.sangui.shop.common.security.SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN), "jwt-1");
        mockMvc.perform(get("/api/admin/products")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-admin-product-list")
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_ADMIN_LISTED"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-product-list"))
                .andExpect(jsonPath("$.data.items[0].skuCount").value(2))
                .andExpect(jsonPath("$.data.items[0].availableStockTotal").value(30));
    }

    @Test
    void createProductUsesPrincipalParameterInsteadOfBodyIdentity() throws Exception {
        when(productCatalogService.createProduct(any(), any()))
                .thenReturn(new ProductDetailResponse(
                        101L,
                        "Sneaker",
                        "Daily trainer",
                        "draft",
                        List.of(new ProductSkuResponse(201L, "shoe-42", "42", 59900L, 20L, 0L))
                ));

        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("ADMIN"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(post("/api/admin/products")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-create-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 999,
                                "userId", "spoof-user",
                                "productName", "Sneaker",
                                "productDescription", "Daily trainer",
                                "skus", List.of(Map.of(
                                        "skuCode", "shoe-42",
                                        "skuName", "42",
                                        "priceCent", 59900
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_CREATED"))
                .andExpect(jsonPath("$.traceId").value("trace-create-product"))
                .andExpect(jsonPath("$.data.status").value("draft"));

        ArgumentCaptor<SanguiPrincipal> principalCaptor = ArgumentCaptor.forClass(SanguiPrincipal.class);
        ArgumentCaptor<CreateProductRequest> requestCaptor = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productCatalogService).createProduct(principalCaptor.capture(), requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().shopId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(principalCaptor.getValue().userId()).isEqualTo("10001");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().shopId()).isEqualTo(999L);
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().userId()).isEqualTo("spoof-user");
    }

    @Test
    void createProductRequiresTrustedPrincipal() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-no-principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productName", "Sneaker",
                                "productDescription", "Daily trainer",
                                "skus", List.of(Map.of(
                                        "skuCode", "shoe-42",
                                        "skuName", "42",
                                        "priceCent", 59900
                                ))
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.traceId").value("trace-no-principal"));
    }

    @Test
    void createProductValidationFailureUsesStableErrorEnvelope() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("ADMIN"), java.util.Set.of(), "jwt-1");
        mockMvc.perform(post("/api/admin/products")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productName", "",
                                "productDescription", "Daily trainer",
                                "skus", List.of()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-validation"));
    }

    @Test
    void updateAndPublishMapServiceErrors() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of("ADMIN"), java.util.Set.of(), "jwt-1");
        when(productCatalogService.updateProduct(any(), eq(101L), any()))
                .thenThrow(new SanguiException(com.sangui.shop.product.domain.ProductErrorCode.PRODUCT_NOT_FOUND, 404));
        when(productCatalogService.publishProduct(any(), eq(101L)))
                .thenThrow(new SanguiException(com.sangui.shop.product.domain.ProductErrorCode.PRODUCT_STATUS_INVALID, 409));

        mockMvc.perform(put("/api/admin/products/101")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-update-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productName", "Sneaker",
                                "productDescription", "Updated",
                                "skus", List.of(Map.of(
                                        "skuCode", "shoe-43",
                                        "skuName", "43",
                                        "priceCent", 69900
                                ))
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/products/101/publish")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-publish-product"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_STATUS_INVALID"));
    }

    @Test
    void statusAndStockAdjustmentEndpointsUseAdminPrincipal() throws Exception {
        SanguiPrincipal principal = new SanguiPrincipal("10001", 1L, java.util.Set.of(), java.util.Set.of(com.sangui.shop.common.security.SanguiPermissionConstants.PRODUCT_CATALOG_ADMIN), "jwt-1");
        when(productCatalogService.updateProductStatus(any(), eq(101L), any()))
                .thenReturn(new ProductDetailResponse(101L, "Sneaker", "Updated", "inactive", List.of()));
        when(productCatalogService.adjustSkuStock(any(), eq(101L), eq(201L), any()))
                .thenReturn(new ProductDetailResponse(101L, "Sneaker", "Updated", "inactive", List.of(new ProductSkuResponse(201L, "shoe-42", "42", 59900L, 25L, 0L))));

        mockMvc.perform(post("/api/admin/products/101/status")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-status-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "inactive",
                                "requestId", "req-status-1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_STATUS_UPDATED"));

        mockMvc.perform(post("/api/admin/products/101/skus/201/stock-adjustments")
                        .requestAttr(SanguiAuthenticationContextFilter.PRINCIPAL_ATTRIBUTE, principal)
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-stock-adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "availableStock", 25,
                                "requestId", "req-stock-1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_SKU_STOCK_ADJUSTED"))
                .andExpect(jsonPath("$.data.skus[0].availableStock").value(25));
    }

    @TestConfiguration
    static class ResolverConfig {

        @Bean
        WebMvcConfigurer sanguiPrincipalResolverConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new SanguiPrincipalArgumentResolver());
                }
            };
        }
    }
}
