package com.sangui.shop.order.application;

import com.sangui.shop.order.client.dto.ProductReviewItemResponse;
import com.sangui.shop.order.client.dto.ProductReviewPageResponse;
import com.sangui.shop.order.client.dto.ProductReviewQueryRequest;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.ProductReviewListItem;
import com.sangui.shop.order.domain.ProductReviewSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductReviewQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderRepository orderRepository;

    public ProductReviewQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public ProductReviewPageResponse listProductReviews(ProductReviewQueryRequest request) {
        int page = normalizePage(request.page());
        int size = normalizeSize(request.size());
        int offset = (page - 1) * size;
        ProductReviewSummary summary = orderRepository.summarizeProductReviews(request.shopId(), request.productId());
        return new ProductReviewPageResponse(
                request.productId(),
                normalizeAverage(summary.averageRating()),
                summary.reviewCount(),
                page,
                size,
                orderRepository.findProductReviews(request.shopId(), request.productId(), offset, size)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    private ProductReviewItemResponse toResponse(ProductReviewListItem item) {
        return new ProductReviewItemResponse(
                item.reviewId(),
                item.rating(),
                item.content(),
                item.imageUrls(),
                OffsetDateTime.of(item.createdAt(), RESPONSE_ZONE.getRules().getOffset(item.createdAt())),
                maskUserId(item.userId()),
                item.skuName()
        );
    }

    private int normalizePage(Integer page) {
        return page == null ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Double normalizeAverage(Double averageRating) {
        if (averageRating == null || averageRating.isNaN()) {
            return 0.0;
        }
        return BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "***";
        }
        String value = userId.trim();
        if (value.length() <= 4) {
            return value.charAt(0) + "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
