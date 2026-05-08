package com.sangui.shop.order.application;

import com.sangui.shop.common.core.error.CommonErrorCode;
import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.CreateOrderReviewRequest;
import com.sangui.shop.order.api.dto.OrderReviewResponse;
import com.sangui.shop.order.domain.OrderErrorCode;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderReviewRecord;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReviewService {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewService.class);
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_IMAGE_COUNT = 6;

    private final OrderRepository orderRepository;

    public OrderReviewService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderReviewResponse createReview(
            SanguiPrincipal principal,
            Long orderId,
            CreateOrderReviewRequest request,
            String traceId
    ) {
        ReviewPayload payload = normalizePayload(request);
        OrderReviewRecord requestReplay = orderRepository.findReviewByRequestId(
                principal.shopId(),
                principal.userId(),
                payload.requestId()
        ).orElse(null);
        if (requestReplay != null) {
            return ensureIdempotentReplay(requestReplay, orderId, payload);
        }

        OrderSnapshot snapshot = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
        OrderRecord order = snapshot.order();
        if (order.status() != OrderStatus.COMPLETED) {
            throw new SanguiException(OrderErrorCode.ORDER_STATUS_INVALID, 409);
        }
        if (snapshot.review() != null) {
            throw new SanguiException(OrderErrorCode.ORDER_REVIEW_ALREADY_EXISTS, 409);
        }

        OrderReviewRecord draft = new OrderReviewRecord(
                null,
                principal.shopId(),
                order.id(),
                order.orderNo(),
                principal.userId(),
                payload.rating(),
                payload.content(),
                payload.imageUrls(),
                payload.requestId(),
                trimToNull(traceId),
                null,
                null
        );
        try {
            Long reviewId = orderRepository.createReview(draft);
            OrderReviewRecord created = orderRepository.findReviewByOrderId(principal.shopId(), order.id())
                    .orElseGet(() -> new OrderReviewRecord(
                            reviewId,
                            draft.shopId(),
                            draft.orderId(),
                            draft.orderNo(),
                            draft.userId(),
                            draft.rating(),
                            draft.content(),
                            draft.imageUrls(),
                            draft.requestId(),
                            draft.traceId(),
                            LocalDateTime.now(),
                            LocalDateTime.now()
                    ));
            logReview(principal, order, payload.requestId(), traceId, payload.rating(), "success");
            return OrderResponseMapper.toReviewResponse(created);
        } catch (DuplicateKeyException exception) {
            OrderReviewRecord replay = orderRepository.findReviewByRequestId(
                    principal.shopId(),
                    principal.userId(),
                    payload.requestId()
            ).orElse(null);
            if (replay != null) {
                return ensureIdempotentReplay(replay, orderId, payload);
            }
            if (orderRepository.findReviewByOrderId(principal.shopId(), order.id()).isPresent()) {
                throw new SanguiException(OrderErrorCode.ORDER_REVIEW_ALREADY_EXISTS, 409);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public OrderReviewResponse getReview(SanguiPrincipal principal, Long orderId) {
        OrderSnapshot snapshot = requireOwnedOrder(principal.shopId(), principal.userId(), orderId);
        return OrderResponseMapper.toReviewResponse(snapshot.review());
    }

    private OrderReviewResponse ensureIdempotentReplay(
            OrderReviewRecord existing,
            Long orderId,
            ReviewPayload payload
    ) {
        if (!Objects.equals(existing.orderId(), orderId) || !matchesPayload(existing, payload)) {
            throw new SanguiException(CommonErrorCode.IDEMPOTENCY_CONFLICT, 409);
        }
        return OrderResponseMapper.toReviewResponse(existing);
    }

    private boolean matchesPayload(OrderReviewRecord existing, ReviewPayload payload) {
        return Objects.equals(existing.requestId(), payload.requestId())
                && Objects.equals(existing.rating(), payload.rating())
                && Objects.equals(existing.content(), payload.content())
                && Objects.equals(existing.imageUrls(), payload.imageUrls());
    }

    private OrderSnapshot requireOwnedOrder(Long shopId, String userId, Long orderId) {
        OrderSnapshot snapshot = orderRepository.findSnapshotById(shopId, orderId)
                .orElseThrow(() -> new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404));
        if (!Objects.equals(snapshot.order().userId(), userId)) {
            throw new SanguiException(OrderErrorCode.ORDER_NOT_FOUND, 404);
        }
        return snapshot;
    }

    private ReviewPayload normalizePayload(CreateOrderReviewRequest request) {
        String requestId = requireText(request.requestId());
        Integer rating = request.rating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        String content = trimToNull(request.content());
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        List<String> imageUrls = request.imageUrls() == null
                ? List.of()
                : request.imageUrls().stream()
                        .map(this::requireText)
                        .toList();
        if (imageUrls.size() > MAX_IMAGE_COUNT) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return new ReviewPayload(requestId, rating, content, imageUrls);
    }

    private void logReview(
            SanguiPrincipal principal,
            OrderRecord order,
            String requestId,
            String traceId,
            Integer rating,
            String outcome
    ) {
        log.info(
                "Order review submission. traceId={} shopId={} userId={} orderId={} orderNo={} requestId={} rating={} outcome={}",
                trimToNull(traceId),
                principal.shopId(),
                principal.userId(),
                order.id(),
                order.orderNo(),
                requestId,
                rating,
                outcome
        );
    }

    private String requireText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new SanguiException(CommonErrorCode.VALIDATION_FAILED, 400);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ReviewPayload(
            String requestId,
            Integer rating,
            String content,
            List<String> imageUrls
    ) {
    }
}
