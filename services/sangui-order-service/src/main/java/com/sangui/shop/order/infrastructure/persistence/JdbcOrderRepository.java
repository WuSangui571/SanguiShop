package com.sangui.shop.order.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.shop.order.domain.AdminOrderQuery;
import com.sangui.shop.order.domain.FulfillmentOrderQuery;
import com.sangui.shop.order.domain.OrderCompensationAttemptQuery;
import com.sangui.shop.order.domain.OrderCompensationAttemptRecord;
import com.sangui.shop.order.domain.OrderCompensationAttemptSummary;
import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderItemDraft;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderReviewRecord;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import com.sangui.shop.order.domain.ProductReviewListItem;
import com.sangui.shop.order.domain.ProductReviewSummary;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final RowMapper<OrderRecord> ORDER_ROW_MAPPER = (rs, rowNum) -> new OrderRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getString("user_id"),
            rs.getString("order_no"),
            rs.getString("request_id"),
            rs.getString("reservation_no"),
            OrderStatus.fromValue(rs.getString("status")),
            rs.getLong("total_amount_cent"),
            rs.getString("trace_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getString("last_compensation_result"),
            rs.getString("last_compensation_error_code"),
            rs.getString("last_compensation_reason"),
            rs.getString("last_compensation_trace_id"),
            rs.getString("last_compensation_trigger"),
            rs.getString("last_compensation_operator"),
            rs.getTimestamp("last_compensated_at") == null ? null : rs.getTimestamp("last_compensated_at").toLocalDateTime(),
            rs.getString("fulfillment_status"),
            rs.getString("carrier"),
            rs.getString("tracking_no"),
            rs.getTimestamp("shipped_at") == null ? null : rs.getTimestamp("shipped_at").toLocalDateTime(),
            rs.getString("shipment_request_id"),
            rs.getString("shipment_trace_id"),
            rs.getString("receipt_request_id"),
            rs.getString("receipt_trace_id"),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime()
    );

    private static final RowMapper<OrderItemRecord> ORDER_ITEM_ROW_MAPPER = (rs, rowNum) -> new OrderItemRecord(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getLong("product_id"),
            rs.getLong("sku_id"),
            rs.getString("sku_name"),
            rs.getLong("price_cent"),
            rs.getInt("quantity"),
            rs.getLong("line_amount_cent")
    );

    private static final RowMapper<OrderCompensationAttemptSummary> ORDER_COMPENSATION_ATTEMPT_SUMMARY_ROW_MAPPER = (rs, rowNum) -> new OrderCompensationAttemptSummary(
            rs.getLong("order_id"),
            rs.getTimestamp("latest_attempt_at").toLocalDateTime(),
            rs.getLong("matched_attempt_count")
    );

    private static final RowMapper<OrderCompensationAttemptRecord> ORDER_COMPENSATION_ATTEMPT_ROW_MAPPER = (rs, rowNum) -> new OrderCompensationAttemptRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getString("reservation_no"),
            rs.getString("result"),
            rs.getString("error_code"),
            rs.getString("reason"),
            rs.getString("trace_id"),
            rs.getString("trigger_type"),
            rs.getString("operator"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OrderRecord> findById(Long shopId, Long orderId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        LIMIT 1
                        """,
                ORDER_ROW_MAPPER,
                shopId,
                orderId
        ).stream().findFirst();
    }

    @Override
    public Optional<OrderSnapshot> findSnapshotById(Long shopId, Long orderId) {
        return findById(shopId, orderId).map(this::toSnapshot);
    }

    @Override
    public Optional<OrderSnapshot> findByRequestId(Long shopId, String userId, String requestId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        WHERE shop_id = ? AND user_id = ? AND request_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                ORDER_ROW_MAPPER,
                shopId,
                userId,
                requestId
        ).stream().findFirst().map(this::toSnapshot);
    }

    @Override
    public Optional<OrderReviewRecord> findReviewByOrderId(Long shopId, Long orderId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, rating, content, image_urls, request_id, trace_id,
                               created_at, updated_at
                        FROM oms_order_review
                        WHERE shop_id = ? AND order_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                this::mapReview,
                shopId,
                orderId
        ).stream().findFirst();
    }

    @Override
    public Optional<OrderReviewRecord> findReviewByRequestId(Long shopId, String userId, String requestId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, rating, content, image_urls, request_id, trace_id,
                               created_at, updated_at
                        FROM oms_order_review
                        WHERE shop_id = ? AND user_id = ? AND request_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                this::mapReview,
                shopId,
                userId,
                requestId
        ).stream().findFirst();
    }

    @Override
    public ProductReviewSummary summarizeProductReviews(Long shopId, Long productId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(r.id) AS review_count, AVG(r.rating) AS average_rating
                        FROM oms_order_review r
                        JOIN oms_order o
                          ON o.shop_id = r.shop_id
                         AND o.id = r.order_id
                         AND o.deleted = 0
                        WHERE r.shop_id = ?
                          AND r.deleted = 0
                          AND o.status = ?
                          AND EXISTS (
                              SELECT 1
                              FROM oms_order_item oi
                              WHERE oi.shop_id = r.shop_id
                                AND oi.order_id = r.order_id
                                AND oi.product_id = ?
                                AND oi.deleted = 0
                          )
                        """,
                (rs, rowNum) -> new ProductReviewSummary(
                        rs.getLong("review_count"),
                        rs.getObject("average_rating") == null ? null : rs.getDouble("average_rating")
                ),
                shopId,
                OrderStatus.COMPLETED.value(),
                productId
        );
    }

    @Override
    public List<ProductReviewListItem> findProductReviews(Long shopId, Long productId, int offset, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT r.id, r.rating, r.content, r.image_urls, r.created_at, r.user_id,
                               (
                                   SELECT oi.sku_name
                                   FROM oms_order_item oi
                                   WHERE oi.shop_id = r.shop_id
                                     AND oi.order_id = r.order_id
                                     AND oi.product_id = ?
                                     AND oi.deleted = 0
                                   ORDER BY oi.id ASC
                                   LIMIT 1
                               ) AS sku_name
                        FROM oms_order_review r
                        JOIN oms_order o
                          ON o.shop_id = r.shop_id
                         AND o.id = r.order_id
                         AND o.deleted = 0
                        WHERE r.shop_id = ?
                          AND r.deleted = 0
                          AND o.status = ?
                          AND EXISTS (
                              SELECT 1
                              FROM oms_order_item oi
                              WHERE oi.shop_id = r.shop_id
                                AND oi.order_id = r.order_id
                                AND oi.product_id = ?
                                AND oi.deleted = 0
                          )
                        ORDER BY r.created_at DESC, r.id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new ProductReviewListItem(
                        rs.getLong("id"),
                        rs.getInt("rating"),
                        rs.getString("content"),
                        fromJson(rs.getString("image_urls")),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("user_id"),
                        rs.getString("sku_name")
                ),
                productId,
                shopId,
                OrderStatus.COMPLETED.value(),
                productId,
                limit,
                offset
        );
    }

    @Override
    public List<OrderSnapshot> findSnapshotsByUser(Long shopId, String userId, int offset, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        WHERE shop_id = ? AND user_id = ? AND deleted = 0
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                ORDER_ROW_MAPPER,
                shopId,
                userId,
                limit,
                offset
        ).stream().map(this::toSnapshot).toList();
    }

    @Override
    public long countByUser(Long shopId, String userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM oms_order
                        WHERE shop_id = ? AND user_id = ? AND deleted = 0
                        """,
                Long.class,
                shopId,
                userId
        );
        return count == null ? 0L : count;
    }

    @Override
    public List<OrderSnapshot> findAdminSnapshots(AdminOrderQuery query, int offset, int limit) {
        QueryParts queryParts = buildAdminOrderWhereClause(query);
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        """
                        + queryParts.sql()
                        + """
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                ORDER_ROW_MAPPER,
                args.toArray()
        ).stream().map(this::toSnapshot).toList();
    }

    @Override
    public long countAdminOrders(AdminOrderQuery query) {
        QueryParts queryParts = buildAdminOrderWhereClause(query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oms_order " + queryParts.sql(),
                Long.class,
                queryParts.args().toArray()
        );
        return count == null ? 0L : count;
    }

    @Override
    public List<OrderSnapshot> findFulfillmentSnapshots(FulfillmentOrderQuery query, int offset, int limit) {
        QueryParts queryParts = buildFulfillmentWhereClause(query);
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        """
                        + queryParts.sql()
                        + """
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                ORDER_ROW_MAPPER,
                args.toArray()
        ).stream().map(this::toSnapshot).toList();
    }

    @Override
    public long countFulfillmentOrders(FulfillmentOrderQuery query) {
        QueryParts queryParts = buildFulfillmentWhereClause(query);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oms_order " + queryParts.sql(),
                Long.class,
                queryParts.args().toArray()
        );
        return count == null ? 0L : count;
    }

    @Override
    public List<OrderRecord> findExpiredCreatedOrders(Long shopId, LocalDateTime createdBefore, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        WHERE shop_id = ? AND status = ? AND created_at <= ? AND deleted = 0
                        ORDER BY id ASC
                        LIMIT ?
                        """,
                ORDER_ROW_MAPPER,
                shopId,
                OrderStatus.CREATED.value(),
                createdBefore,
                limit
        );
    }

    @Override
    public List<OrderRecord> findCancelledOrders(Long shopId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at, fulfillment_status, carrier, tracking_no,
                               shipped_at, shipment_request_id, shipment_trace_id, receipt_request_id, receipt_trace_id, completed_at
                        FROM oms_order
                        WHERE shop_id = ? AND status = ? AND deleted = 0
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ?
                        """,
                ORDER_ROW_MAPPER,
                shopId,
                OrderStatus.CANCELLED.value(),
                limit
        );
    }

    @Override
    public long countCompensationAttempts(OrderCompensationAttemptQuery query) {
        QueryParts queryParts = buildCompensationAttemptWhereClause(query);
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT order_id) FROM oms_order_compensation_attempt " + queryParts.sql(),
                Long.class,
                queryParts.args().toArray()
        );
    }

    @Override
    public List<OrderCompensationAttemptSummary> findCompensationAttemptSummaries(
            OrderCompensationAttemptQuery query,
            int offset,
            int limit
    ) {
        QueryParts queryParts = buildCompensationAttemptWhereClause(query);
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                """
                        SELECT order_id,
                               MAX(created_at) AS latest_attempt_at,
                               COUNT(*) AS matched_attempt_count
                        FROM oms_order_compensation_attempt
                        """
                        + queryParts.sql()
                        + """
                        GROUP BY order_id
                        ORDER BY latest_attempt_at DESC, order_id DESC
                        LIMIT ? OFFSET ?
                        """,
                ORDER_COMPENSATION_ATTEMPT_SUMMARY_ROW_MAPPER,
                args.toArray()
        );
    }

    @Override
    public List<OrderCompensationAttemptRecord> findCompensationAttemptsByOrderIds(Long shopId, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(orderIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(shopId);
        args.addAll(orderIds);
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, reservation_no, result, error_code, reason, trace_id,
                               trigger_type, operator, created_at, updated_at
                        FROM oms_order_compensation_attempt
                        WHERE shop_id = ? AND deleted = 0 AND order_id IN (
                        """
                        + placeholders
                        + """
                        )
                        ORDER BY order_id ASC, created_at DESC, id DESC
                        """,
                ORDER_COMPENSATION_ATTEMPT_ROW_MAPPER,
                args.toArray()
        );
    }

    @Override
    public Long createOrder(Long shopId, String userId, String orderNo, String traceId, OrderStatus status, OrderCreateDraft draft) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO oms_order (
                                shop_id, order_no, user_id, request_id, reservation_no, trace_id, status, total_amount_cent
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, shopId);
            statement.setString(2, orderNo);
            statement.setString(3, userId);
            statement.setString(4, draft.requestId());
            statement.setString(5, draft.reservationNo());
            statement.setString(6, traceId);
            statement.setString(7, status.value());
            statement.setLong(8, draft.totalAmountCent());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Order insert did not return a generated id");
        }
        Long orderId = key.longValue();
        insertItems(shopId, orderId, draft.items());
        return orderId;
    }

    @Override
    public Long createReview(OrderReviewRecord review) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO oms_order_review (
                                shop_id, order_id, order_no, user_id, rating, content, image_urls, request_id, trace_id
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, review.shopId());
            statement.setLong(2, review.orderId());
            statement.setString(3, review.orderNo());
            statement.setString(4, review.userId());
            statement.setInt(5, review.rating());
            statement.setString(6, review.content());
            statement.setString(7, toJson(review.imageUrls()));
            statement.setString(8, review.requestId());
            statement.setString(9, review.traceId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Order review insert did not return a generated id");
        }
        return key.longValue();
    }

    @Override
    public int updateStatus(Long shopId, Long orderId, OrderStatus currentStatus, OrderStatus nextStatus) {
        return jdbcTemplate.update(
                """
                        UPDATE oms_order
                        SET status = ?
                        WHERE shop_id = ? AND id = ? AND status = ? AND deleted = 0
                        """,
                nextStatus.value(),
                shopId,
                orderId,
                currentStatus.value()
        );
    }

    @Override
    public int markShipped(
            Long shopId,
            Long orderId,
            String requestId,
            String carrier,
            String trackingNo,
            String traceId,
            LocalDateTime shippedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE oms_order
                        SET status = ?,
                            fulfillment_status = ?,
                            carrier = ?,
                            tracking_no = ?,
                            shipped_at = ?,
                            shipment_request_id = ?,
                            shipment_trace_id = ?
                        WHERE shop_id = ? AND id = ? AND status = ? AND deleted = 0
                        """,
                OrderStatus.SHIPPED.value(),
                OrderStatus.SHIPPED.value(),
                carrier,
                trackingNo,
                shippedAt,
                requestId,
                traceId,
                shopId,
                orderId,
                OrderStatus.PAID.value()
        );
    }

    @Override
    public int markCompleted(
            Long shopId,
            Long orderId,
            String requestId,
            String traceId,
            LocalDateTime completedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE oms_order
                        SET status = ?,
                            fulfillment_status = ?,
                            receipt_request_id = ?,
                            receipt_trace_id = ?,
                            completed_at = ?
                        WHERE shop_id = ? AND id = ? AND status = ? AND deleted = 0
                        """,
                OrderStatus.COMPLETED.value(),
                OrderStatus.COMPLETED.value(),
                requestId,
                traceId,
                completedAt,
                shopId,
                orderId,
                OrderStatus.SHIPPED.value()
        );
    }

    @Override
    public void updateCompensationMetadata(
            Long shopId,
            Long orderId,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator,
            LocalDateTime compensatedAt
    ) {
        jdbcTemplate.update(
                """
                        UPDATE oms_order
                        SET last_compensation_result = ?,
                            last_compensation_error_code = ?,
                            last_compensation_reason = ?,
                            last_compensation_trace_id = ?,
                            last_compensation_trigger = ?,
                            last_compensation_operator = ?,
                            last_compensated_at = ?
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        """,
                result,
                errorCode,
                reason,
                traceId,
                trigger,
                operator,
                compensatedAt,
                shopId,
                orderId
        );
    }

    @Override
    public void appendCompensationAttempt(
            Long shopId,
            Long orderId,
            String orderNo,
            String reservationNo,
            String result,
            String errorCode,
            String reason,
            String traceId,
            String trigger,
            String operator
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO oms_order_compensation_attempt (
                            shop_id, order_id, order_no, reservation_no, result, error_code, reason, trace_id, trigger_type, operator
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                shopId,
                orderId,
                orderNo,
                reservationNo,
                result,
                errorCode,
                reason,
                traceId,
                trigger,
                operator
        );
    }

    private OrderSnapshot toSnapshot(OrderRecord order) {
        List<OrderItemRecord> items = jdbcTemplate.query(
                """
                        SELECT id, order_id, product_id, sku_id, sku_name, price_cent, quantity, line_amount_cent
                        FROM oms_order_item
                        WHERE shop_id = ? AND order_id = ? AND deleted = 0
                        ORDER BY id ASC
                        """,
                ORDER_ITEM_ROW_MAPPER,
                order.shopId(),
                order.id()
        );
        OrderReviewRecord review = findReviewByOrderId(order.shopId(), order.id()).orElse(null);
        return new OrderSnapshot(order, items, review);
    }

    private OrderReviewRecord mapReview(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrderReviewRecord(
                rs.getLong("id"),
                rs.getLong("shop_id"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getString("user_id"),
                rs.getInt("rating"),
                rs.getString("content"),
                fromJson(rs.getString("image_urls")),
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize order review image urls", exception);
        }
    }

    private List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize order review image urls", exception);
        }
    }

    private void insertItems(Long shopId, Long orderId, List<OrderItemDraft> items) {
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO oms_order_item (
                            shop_id, order_id, product_id, sku_id, sku_name, price_cent, quantity, line_amount_cent
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                items,
                items.size(),
                (PreparedStatement statement, OrderItemDraft item) -> {
                    statement.setLong(1, shopId);
                    statement.setLong(2, orderId);
                    statement.setLong(3, item.productId());
                    statement.setLong(4, item.skuId());
                    statement.setString(5, item.skuName());
                    statement.setLong(6, item.priceCent());
                    statement.setInt(7, item.quantity());
                    statement.setLong(8, item.lineAmountCent());
                }
        );
    }

    private QueryParts buildCompensationAttemptWhereClause(OrderCompensationAttemptQuery query) {
        StringBuilder sql = new StringBuilder("WHERE shop_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(query.shopId());
        if (query.orderId() != null) {
            sql.append(" AND order_id = ?");
            args.add(query.orderId());
        }
        if (query.trigger() != null) {
            sql.append(" AND trigger_type = ?");
            args.add(query.trigger());
        }
        if (query.result() != null) {
            sql.append(" AND result = ?");
            args.add(query.result());
        }
        if (query.operator() != null) {
            sql.append(" AND operator = ?");
            args.add(query.operator());
        }
        if (query.traceId() != null) {
            sql.append(" AND trace_id = ?");
            args.add(query.traceId());
        }
        if (query.fromTime() != null) {
            sql.append(" AND created_at >= ?");
            args.add(query.fromTime());
        }
        if (query.toTime() != null) {
            sql.append(" AND created_at <= ?");
            args.add(query.toTime());
        }
        return new QueryParts(sql.toString(), args);
    }

    private QueryParts buildAdminOrderWhereClause(AdminOrderQuery query) {
        StringBuilder sql = new StringBuilder("WHERE shop_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(query.shopId());
        if (query.status() != null) {
            sql.append(" AND status = ?");
            args.add(query.status().value());
        }
        if (query.orderNo() != null) {
            sql.append(" AND order_no LIKE ?");
            args.add("%" + query.orderNo() + "%");
        }
        if (query.userId() != null) {
            sql.append(" AND user_id = ?");
            args.add(query.userId());
        }
        if (query.fromTime() != null) {
            sql.append(" AND created_at >= ?");
            args.add(query.fromTime());
        }
        if (query.toTime() != null) {
            sql.append(" AND created_at <= ?");
            args.add(query.toTime());
        }
        return new QueryParts(sql.toString(), args);
    }

    private QueryParts buildFulfillmentWhereClause(FulfillmentOrderQuery query) {
        StringBuilder sql = new StringBuilder("WHERE shop_id = ? AND deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(query.shopId());
        String status = query.fulfillmentStatus();
        if ("unshipped".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            args.add(OrderStatus.PAID.value());
        } else if ("shipped".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            args.add(OrderStatus.SHIPPED.value());
        } else {
            sql.append(" AND status IN (?, ?)");
            args.add(OrderStatus.PAID.value());
            args.add(OrderStatus.SHIPPED.value());
        }
        if (query.orderNo() != null) {
            sql.append(" AND order_no LIKE ?");
            args.add("%" + query.orderNo() + "%");
        }
        if (query.userId() != null) {
            sql.append(" AND user_id = ?");
            args.add(query.userId());
        }
        if (query.fromTime() != null) {
            sql.append(" AND created_at >= ?");
            args.add(query.fromTime());
        }
        if (query.toTime() != null) {
            sql.append(" AND created_at <= ?");
            args.add(query.toTime());
        }
        return new QueryParts(sql.toString(), args);
    }

    private record QueryParts(String sql, List<Object> args) {
    }
}
