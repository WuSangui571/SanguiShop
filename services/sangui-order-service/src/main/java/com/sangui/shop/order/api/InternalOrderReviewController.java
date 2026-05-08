package com.sangui.shop.order.api;

import com.sangui.shop.common.core.api.ApiResult;
import com.sangui.shop.common.core.trace.TraceConstants;
import com.sangui.shop.order.application.ProductReviewQueryService;
import com.sangui.shop.order.client.dto.ProductReviewPageResponse;
import com.sangui.shop.order.client.dto.ProductReviewQueryRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderReviewController {

    private final ProductReviewQueryService productReviewQueryService;

    public InternalOrderReviewController(ProductReviewQueryService productReviewQueryService) {
        this.productReviewQueryService = productReviewQueryService;
    }

    @PostMapping("/reviews/by-product/query")
    public ApiResult<ProductReviewPageResponse> listProductReviews(
            @Valid @RequestBody ProductReviewQueryRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductReviewPageResponse response = productReviewQueryService.listProductReviews(request);
        return ApiResult.ok("PRODUCT_REVIEWS_FETCHED", response, traceId(httpRequest));
    }

    private String traceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceConstants.TRACE_ID);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getHeader(TraceConstants.TRACE_ID_HEADER);
    }
}
