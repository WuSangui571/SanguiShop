package com.sangui.shop.order.infrastructure.persistence;

import com.sangui.shop.order.domain.OrderCreateDraft;
import com.sangui.shop.order.domain.OrderItemDraft;
import com.sangui.shop.order.domain.OrderItemRecord;
import com.sangui.shop.order.domain.OrderRecord;
import com.sangui.shop.order.domain.OrderRepository;
import com.sangui.shop.order.domain.OrderSnapshot;
import com.sangui.shop.order.domain.OrderStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private static final RowMapper<OrderRecord> ORDER_ROW_MAPPER = (rs, rowNum) -> new OrderRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getString("user_id"),
            rs.getString("order_no"),
            rs.getString("request_id"),
            rs.getString("reservation_no"),
            OrderStatus.fromValue(rs.getString("status")),
            rs.getLong("total_amount_cent"),
            rs.getString("trace_id")
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

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OrderRecord> findById(Long shopId, Long orderId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id
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
                        SELECT id, shop_id, user_id, order_no, request_id, reservation_no, status, total_amount_cent, trace_id
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
        return new OrderSnapshot(order, items);
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
}
