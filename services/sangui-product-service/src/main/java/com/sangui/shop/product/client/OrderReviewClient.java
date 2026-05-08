package com.sangui.shop.product.client;

import com.sangui.shop.product.client.dto.ProductReviewPageResponse;

public interface OrderReviewClient {

    ProductReviewPageResponse listProductReviews(
            Long shopId,
            Long productId,
            int page,
            int size,
            boolean withImages,
            String traceId
    );
}
