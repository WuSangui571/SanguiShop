package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sangui.shop.common.core.exception.SanguiException;
import com.sangui.shop.common.security.SanguiPrincipal;
import com.sangui.shop.order.api.dto.CreateOrderItemRequest;
import com.sangui.shop.order.api.dto.CreateOrderRequest;
import com.sangui.shop.order.api.dto.OrderResponse;
import com.sangui.shop.order.client.ProductCatalogClient;
import com.sangui.shop.order.client.ProductSkuSnapshot;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderNumberGenerator;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCreateServiceTest {

    private static final SanguiPrincipal USER_PRINCIPAL = new SanguiPrincipal(
            "10001",
            1L,
            java.util.Set.of("USER"),
            java.util.Set.of("order:create"),
            "jwt-user"
    );

    private InMemoryOrderRepository orderRepository;
    private StubProductCatalogClient productCatalogClient;
    private OrderCreateService orderCreateService;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        productCatalogClient = new StubProductCatalogClient();
        orderCreateService = new OrderCreateService(orderRepository, productCatalogClient, new FixedOrderNumberGenerator("ORDTEST0001"));
    }

    @Test
    void createOrderUsesPrincipalIdentityAndPersistsSkuSnapshots() {
        productCatalogClient.seed(
                new ProductSkuSnapshot(301L, 401L, "shoe-42", "Sneaker 42", 59900L),
                new ProductSkuSnapshot(302L, 402L, "sock-1", "Sports Socks", 1500L)
        );

        OrderResponse response = orderCreateService.createOrder(
                USER_PRINCIPAL,
                new CreateOrderRequest(
                        999L,
                        "spoof-user",
                        "req-001",
                        List.of(
                                new CreateOrderItemRequest(401L, 2),
                                new CreateOrderItemRequest(402L, 3)
                        )
                ),
                "trace-order-create"
        );

        assertThat(response.orderId()).isEqualTo(10001L);
        assertThat(response.orderNo()).isEqualTo("ORDTEST0001");
        assertThat(response.shopId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo("10001");
        assertThat(response.status()).isEqualTo("created");
        assertThat(response.totalAmountCent()).isEqualTo(124300L);
        assertThat(response.items()).hasSize(2);
        assertThat(orderRepository.lastCreatedShopId).isEqualTo(1L);
        assertThat(orderRepository.lastCreatedUserId).isEqualTo("10001");
        assertThat(orderRepository.lastTraceId).isEqualTo("trace-order-create");
    }

    @Test
    void createOrderReturnsExistingOrderForSameRequestIdAndSamePayload() {
        productCatalogClient.seed(new ProductSkuSnapshot(301L, 401L, "shoe-42", "Sneaker 42", 59900L));
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                "ignored-user",
                "req-001",
                List.of(new CreateOrderItemRequest(401L, 1))
        );

        OrderResponse first = orderCreateService.createOrder(USER_PRINCIPAL, request, "trace-1");
        OrderResponse second = orderCreateService.createOrder(USER_PRINCIPAL, request, "trace-2");

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.orderNo()).isEqualTo(first.orderNo());
        assertThat(orderRepository.createCalls).isEqualTo(1);
    }

    @Test
    void createOrderRejectsRequestIdReuseWithDifferentPayload() {
        productCatalogClient.seed(
                new ProductSkuSnapshot(301L, 401L, "shoe-42", "Sneaker 42", 59900L),
                new ProductSkuSnapshot(302L, 402L, "sock-1", "Sports Socks", 1500L)
        );

        orderCreateService.createOrder(
                USER_PRINCIPAL,
                new CreateOrderRequest(1L, "u", "req-001", List.of(new CreateOrderItemRequest(401L, 1))),
                "trace-1"
        );

        assertThatThrownBy(() -> orderCreateService.createOrder(
                USER_PRINCIPAL,
                new CreateOrderRequest(1L, "u", "req-001", List.of(new CreateOrderItemRequest(402L, 1))),
                "trace-2"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    @Test
    void createOrderRejectsMissingSkuSnapshot() {
        productCatalogClient.seed(new ProductSkuSnapshot(301L, 401L, "shoe-42", "Sneaker 42", 59900L));

        assertThatThrownBy(() -> orderCreateService.createOrder(
                USER_PRINCIPAL,
                new CreateOrderRequest(1L, "u", "req-001", List.of(new CreateOrderItemRequest(999L, 1))),
                "trace-1"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo("ORDER_SKU_NOT_FOUND");
            assertThat(exception.httpStatus()).isEqualTo(404);
        });
    }

    @Test
    void createOrderRejectsDuplicateSkuIds() {
        productCatalogClient.seed(new ProductSkuSnapshot(301L, 401L, "shoe-42", "Sneaker 42", 59900L));

        assertThatThrownBy(() -> orderCreateService.createOrder(
                USER_PRINCIPAL,
                new CreateOrderRequest(
                        1L,
                        "u",
                        "req-001",
                        List.of(new CreateOrderItemRequest(401L, 1), new CreateOrderItemRequest(401L, 2))
                ),
                "trace-1"
        )).isInstanceOfSatisfying(SanguiException.class, exception -> {
            assertThat(exception.errorCode().code()).isEqualTo("ORDER_SKU_DUPLICATED");
            assertThat(exception.httpStatus()).isEqualTo(409);
        });
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final AtomicLong nextOrderId = new AtomicLong(10000);
        private final AtomicLong nextOrderItemId = new AtomicLong(20000);
        private final Map<String, OrderSnapshot> snapshotsByRequestKey = new LinkedHashMap<>();
        private Long lastCreatedShopId;
        private String lastCreatedUserId;
        private String lastTraceId;
        private int createCalls;

        @Override
        public Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId) {
            return Optional.ofNullable(snapshotsByRequestKey.get(key(shopId, userId, requestId)));
        }

        @Override
        public Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft) {
            createCalls++;
            lastCreatedShopId = shopId;
            lastCreatedUserId = userId;
            lastTraceId = traceId;
            Long orderId = nextOrderId.incrementAndGet();
            List<OrderItemRecord> items = draft.items().stream()
                    .map(item -> new OrderItemRecord(
                            nextOrderItemId.incrementAndGet(),
                            orderId,
                            item.productId(),
                            item.skuId(),
                            item.skuName(),
                            item.priceCent(),
                            item.quantity(),
                            item.lineAmountCent()
                    ))
                    .toList();
            OrderSnapshot snapshot = new OrderSnapshot(
                    new OrderRecord(orderId, shopId, userId, orderNo, draft.requestId(), status, draft.totalAmountCent(), traceId),
                    items
            );
            snapshotsByRequestKey.put(key(shopId, userId, draft.requestId()), snapshot);
            return orderId;
        }

        private String key(Long shopId, String userId, String requestId) {
            return shopId + "|" + userId + "|" + requestId;
        }
    }

    private static final class StubProductCatalogClient implements ProductCatalogClient {

        private final Map<Long, ProductSkuSnapshot> snapshotsBySkuId = new LinkedHashMap<>();

        @Override
        public List<ProductSkuSnapshot> listActiveSkuSnapshots(Long shopId, List<Long> skuIds) {
            return skuIds.stream()
                    .map(snapshotsBySkuId::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        private void seed(ProductSkuSnapshot... snapshots) {
            for (ProductSkuSnapshot snapshot : snapshots) {
                snapshotsBySkuId.put(snapshot.skuId(), snapshot);
            }
        }
    }

    private record FixedOrderNumberGenerator(String orderNo) implements OrderNumberGenerator {
        @Override
        public String nextOrderNo() {
            return orderNo;
        }
    }
}
