package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sangui.shop.order.client.dto.ProductReviewPageResponse;
import com.sangui.shop.order.client.dto.ProductReviewQueryRequest;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.ProductReviewListItem;
import com.sangui.shop.order.domain.ProductReviewSummary;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductReviewQueryServiceTest {

    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final ProductReviewQueryService service = new ProductReviewQueryService(orderRepository);

    @Test
    void returnsPagedProductReviewsWithSummaryAndMaskedUsers() {
        when(orderRepository.summarizeProductReviews(1L, 301L))
                .thenReturn(new ProductReviewSummary(2L, 4.46));
        when(orderRepository.findProductReviews(1L, 301L, 0, 10))
                .thenReturn(List.of(
                        new ProductReviewListItem(
                                9002L,
                                4,
                                "Good fit.",
                                List.of(),
                                LocalDateTime.of(2026, 5, 8, 11, 0),
                                "10001",
                                "Size 43"
                        ),
                        new ProductReviewListItem(
                                9001L,
                                5,
                                "Matched expectations.",
                                List.of("https://cdn.example/review.jpg"),
                                LocalDateTime.of(2026, 5, 8, 10, 0),
                                "u1",
                                "Size 42"
                        )
                ));

        ProductReviewPageResponse response = service.listProductReviews(new ProductReviewQueryRequest(1L, 301L, 1, 10));

        assertThat(response.productId()).isEqualTo(301L);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviewCount()).isEqualTo(2L);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().maskedUserId()).isEqualTo("10***01");
        assertThat(response.items().get(1).maskedUserId()).isEqualTo("u***");
        assertThat(response.items().getFirst().skuName()).isEqualTo("Size 43");
    }

    @Test
    void returnsEmptySummaryForProductWithoutReviewsAndCapsSize() {
        when(orderRepository.summarizeProductReviews(1L, 301L))
                .thenReturn(new ProductReviewSummary(0L, null));
        when(orderRepository.findProductReviews(1L, 301L, 0, 50))
                .thenReturn(List.of());

        ProductReviewPageResponse response = service.listProductReviews(new ProductReviewQueryRequest(1L, 301L, null, 200));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
        assertThat(response.averageRating()).isEqualTo(0.0);
        assertThat(response.reviewCount()).isZero();
        assertThat(response.items()).isEmpty();
    }
}
