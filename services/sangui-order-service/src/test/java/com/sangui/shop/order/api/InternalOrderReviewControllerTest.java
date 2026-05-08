package com.sangui.shop.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.common.web.GlobalApiExceptionHandler;
import com.sangui.shop.order.application.ProductReviewQueryService;
import com.sangui.shop.order.client.dto.ProductReviewItemResponse;
import com.sangui.shop.order.client.dto.ProductReviewPageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalOrderReviewController.class)
@Import(GlobalApiExceptionHandler.class)
class InternalOrderReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductReviewQueryService productReviewQueryService;

    @Test
    void listProductReviewsReturnsInternalProjectionEnvelope() throws Exception {
        when(productReviewQueryService.listProductReviews(any()))
                .thenReturn(new ProductReviewPageResponse(
                        301L,
                        5.0,
                        1L,
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

        mockMvc.perform(post("/internal/orders/reviews/by-product/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-review-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "productId", 301,
                                "page", 1,
                                "size", 10
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRODUCT_REVIEWS_FETCHED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-review-query"))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(9001))
                .andExpect(jsonPath("$.data.items[0].maskedUserId").value("10***01"))
                .andExpect(jsonPath("$.data.items[0].orderNo").doesNotExist());
    }

    @Test
    void listProductReviewsValidatesRequest() throws Exception {
        mockMvc.perform(post("/internal/orders/reviews/by-product/query")
                        .header(TraceConstants.TRACE_ID_HEADER, "trace-product-review-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopId", 1,
                                "productId", 0,
                                "page", 0,
                                "size", 51
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("trace-product-review-invalid"));
    }
}
