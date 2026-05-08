package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sangui.shop.order.client.dto.ProductReviewPageResponse;
import com.sangui.shop.order.client.dto.ProductReviewQueryRequest;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.ProductReviewListItem;
import com.sangui.shop.order.domain.ProductReviewSummary;
import com.sangui.shop.order.domain.ReviewVisibilityStatus;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductReviewQueryServiceTest {

    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final ProductReviewQueryService service = new ProductReviewQueryService(orderRepository);

    @Test
    void returnsPagedProductReviewsWithSummaryAndMaskedUsers() {
        when(orderRepository.summarizeProductReviews(1L, 301L, false))
                .thenReturn(new ProductReviewSummary(2L, 4.46, Map.of(4, 1L, 5, 1L)));
        when(orderRepository.findProductReviews(1L, 301L, false, 0, 10))
                .thenReturn(List.of(
                        new ProductReviewListItem(
                                9002L,
                                4,
                                "Good fit.",
                                List.of(),
                                LocalDateTime.of(2026, 5, 8, 11, 0),
                                "10001",
                                "Size 43",
                                "Thanks for the feedback.",
                                ReviewVisibilityStatus.VISIBLE,
                                LocalDateTime.of(2026, 5, 8, 12, 0)
                        ),
                        new ProductReviewListItem(
                                9001L,
                                5,
                                "Matched expectations.",
                                List.of("https://cdn.example/review.jpg"),
                                LocalDateTime.of(2026, 5, 8, 10, 0),
                                "u1",
                                "Size 42",
                                "Hidden reply",
                                ReviewVisibilityStatus.HIDDEN,
                                LocalDateTime.of(2026, 5, 8, 12, 0)
                        )
                ));

        ProductReviewPageResponse response = service.listProductReviews(new ProductReviewQueryRequest(
                1L,
                301L,
                1,
                10,
                false
        ));

        assertThat(response.productId()).isEqualTo(301L);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviewCount()).isEqualTo(2L);
        assertThat(response.ratingDistribution()).containsEntry(1, 0L).containsEntry(4, 1L).containsEntry(5, 1L);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().maskedUserId()).isEqualTo("10***01");
        assertThat(response.items().get(1).maskedUserId()).isEqualTo("u***");
        assertThat(response.items().get(1).imageUrls()).containsExactly("https://cdn.example/review.jpg");
        assertThat(response.items().getFirst().skuName()).isEqualTo("Size 43");
        assertThat(response.items().getFirst().merchantReply()).isNotNull();
        assertThat(response.items().getFirst().merchantReply().content()).isEqualTo("Thanks for the feedback.");
        assertThat(response.items().get(1).merchantReply()).isNull();
    }

    @Test
    void returnsEmptySummaryForProductWithoutReviewsAndCapsSize() {
        when(orderRepository.summarizeProductReviews(1L, 301L, false))
                .thenReturn(new ProductReviewSummary(0L, null, Map.of()));
        when(orderRepository.findProductReviews(1L, 301L, false, 0, 50))
                .thenReturn(List.of());

        ProductReviewPageResponse response = service.listProductReviews(new ProductReviewQueryRequest(
                1L,
                301L,
                null,
                200,
                null
        ));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
        assertThat(response.averageRating()).isEqualTo(0.0);
        assertThat(response.reviewCount()).isZero();
        assertThat(response.ratingDistribution()).containsOnly(
                Map.entry(1, 0L),
                Map.entry(2, 0L),
                Map.entry(3, 0L),
                Map.entry(4, 0L),
                Map.entry(5, 0L)
        );
        assertThat(response.items()).isEmpty();
    }

    @Test
    void passesWithImagesFilterToSummaryAndListQueries() {
        when(orderRepository.summarizeProductReviews(1L, 301L, true))
                .thenReturn(new ProductReviewSummary(1L, 5.0, Map.of(5, 1L)));
        when(orderRepository.findProductReviews(1L, 301L, true, 5, 5))
                .thenReturn(List.of());

        ProductReviewPageResponse response = service.listProductReviews(new ProductReviewQueryRequest(
                1L,
                301L,
                2,
                5,
                true
        ));

        assertThat(response.reviewCount()).isEqualTo(1L);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.ratingDistribution()).containsEntry(5, 1L);
    }
}
