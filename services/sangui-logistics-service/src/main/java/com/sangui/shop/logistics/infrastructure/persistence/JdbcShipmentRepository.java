package com.sangui.shop.logistics.infrastructure.persistence;

import com.sangui.shop.logistics.domain.ShipmentRecord;
import com.sangui.shop.logistics.domain.ShipmentRepository;
import com.sangui.shop.logistics.domain.ShipmentStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShipmentRepository implements ShipmentRepository {

    private static final RowMapper<ShipmentRecord> ROW_MAPPER = (rs, rowNum) -> new ShipmentRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getString("user_id"),
            rs.getString("carrier"),
            rs.getString("tracking_no"),
            ShipmentStatus.SHIPPED,
            rs.getString("request_id"),
            rs.getString("trace_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcShipmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ShipmentRecord> findByOrderId(Long shopId, Long orderId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, carrier, tracking_no, status, request_id,
                               trace_id, created_at, updated_at
                        FROM lgs_shipment
                        WHERE shop_id = ? AND order_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                ROW_MAPPER,
                shopId,
                orderId
        ).stream().findFirst();
    }

    @Override
    public Optional<ShipmentRecord> findByRequestId(Long shopId, String requestId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, carrier, tracking_no, status, request_id,
                               trace_id, created_at, updated_at
                        FROM lgs_shipment
                        WHERE shop_id = ? AND request_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                ROW_MAPPER,
                shopId,
                requestId
        ).stream().findFirst();
    }

    @Override
    public Long create(ShipmentRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO lgs_shipment (
                                shop_id, order_id, order_no, user_id, carrier, tracking_no, status, request_id, trace_id
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, record.shopId());
            statement.setLong(2, record.orderId());
            statement.setString(3, record.orderNo());
            statement.setString(4, record.userId());
            statement.setString(5, record.carrier());
            statement.setString(6, record.trackingNo());
            statement.setString(7, record.status().value());
            statement.setString(8, record.requestId());
            statement.setString(9, record.traceId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Shipment insert did not return a generated id");
        }
        return key.longValue();
    }
}
