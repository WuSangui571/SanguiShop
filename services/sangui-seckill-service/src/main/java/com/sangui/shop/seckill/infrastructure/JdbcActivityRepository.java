package com.sangui.shop.seckill.infrastructure;

import com.sangui.shop.seckill.domain.ActivityRepository;
import com.sangui.shop.seckill.domain.SeckillActivity;
import com.sangui.shop.seckill.domain.SeckillActivitySku;
import com.sangui.shop.seckill.domain.SeckillActivityStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcActivityRepository implements ActivityRepository {

    private static final RowMapper<SeckillActivity> ACTIVITY_ROW_MAPPER = (rs, rowNum) -> new SeckillActivity(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getString("activity_name"),
            rs.getString("description"),
            SeckillActivityStatus.fromValue(rs.getString("status")),
            rs.getTimestamp("starts_at").toLocalDateTime(),
            rs.getTimestamp("ends_at").toLocalDateTime(),
            rs.getString("request_id"),
            rs.getString("trace_id"),
            null,
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static final RowMapper<SeckillActivitySku> SKU_ROW_MAPPER = (rs, rowNum) -> new SeckillActivitySku(
            rs.getLong("id"),
            rs.getLong("activity_id"),
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getLong("sku_id"),
            rs.getString("sku_code"),
            rs.getString("sku_name"),
            rs.getLong("price_cent"),
            rs.getLong("seckill_price_cent"),
            rs.getLong("available_stock"),
            rs.getLong("activity_stock"),
            rs.getLong("sold_count"),
            rs.getString("request_id")
    );

    private static final RowMapper<StatusRequestRecord> STATUS_REQUEST_ROW_MAPPER = (rs, rowNum) -> new StatusRequestRecord(
            rs.getLong("shop_id"),
            rs.getLong("activity_id"),
            rs.getString("request_id"),
            SeckillActivityStatus.fromValue(rs.getString("target_status"))
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeckillActivity> findById(Long shopId, Long activityId) {
        List<SeckillActivity> activities = jdbcTemplate.query(
                """
                        SELECT id, shop_id, activity_name, description, status, starts_at, ends_at,
                               request_id, trace_id, created_at, updated_at
                        FROM sk_activity
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        """,
                ACTIVITY_ROW_MAPPER,
                shopId, activityId
        );
        if (activities.isEmpty()) {
            return Optional.empty();
        }
        SeckillActivity activity = activities.getFirst();
        List<SeckillActivitySku> skus = findSkusByActivityId(shopId, activityId);
        return Optional.of(activity.withSkuBound(skus));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeckillActivity> findByRequestId(Long shopId, String requestId) {
        List<SeckillActivity> activities = jdbcTemplate.query(
                """
                        SELECT id, shop_id, activity_name, description, status, starts_at, ends_at,
                               request_id, trace_id, created_at, updated_at
                        FROM sk_activity
                        WHERE shop_id = ? AND request_id = ? AND deleted = 0
                        """,
                ACTIVITY_ROW_MAPPER,
                shopId, requestId
        );
        if (activities.isEmpty()) {
            return Optional.empty();
        }
        SeckillActivity activity = activities.getFirst();
        List<SeckillActivitySku> skus = findSkusByActivityId(shopId, activity.id());
        return Optional.of(activity.withSkuBound(skus));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeckillActivity> findPage(Long shopId, String status, int offset, int size) {
        List<SeckillActivity> activities;
        if (status == null) {
            activities = jdbcTemplate.query(
                    """
                            SELECT id, shop_id, activity_name, description, status, starts_at, ends_at,
                                   request_id, trace_id, created_at, updated_at
                            FROM sk_activity
                            WHERE shop_id = ? AND deleted = 0
                            ORDER BY created_at DESC, id DESC
                            LIMIT ? OFFSET ?
                            """,
                    ACTIVITY_ROW_MAPPER,
                    shopId, size, offset
            );
        } else {
            activities = jdbcTemplate.query(
                    """
                            SELECT id, shop_id, activity_name, description, status, starts_at, ends_at,
                                   request_id, trace_id, created_at, updated_at
                            FROM sk_activity
                            WHERE shop_id = ? AND status = ? AND deleted = 0
                            ORDER BY created_at DESC, id DESC
                            LIMIT ? OFFSET ?
                            """,
                    ACTIVITY_ROW_MAPPER,
                    shopId, status, size, offset
            );
        }
        return activities.stream()
                .map(activity -> activity.withSkuBound(findSkusByActivityId(shopId, activity.id())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int count(Long shopId, String status) {
        Long total;
        if (status == null) {
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sk_activity WHERE shop_id = ? AND deleted = 0",
                    Long.class, shopId
            );
        } else {
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sk_activity WHERE shop_id = ? AND status = ? AND deleted = 0",
                    Long.class, shopId, status
            );
        }
        return total == null ? 0 : total.intValue();
    }

    @Override
    @Transactional
    public Long create(SeckillActivity activity, List<SeckillActivitySku> skus) {
        if (activity.id() != null) {
            SeckillActivity existing = jdbcTemplate.queryForObject(
                    "SELECT id FROM sk_activity WHERE shop_id = ? AND id = ? AND deleted = 0",
                    (rs, rowNum) -> new SeckillActivity(
                            rs.getLong("id"), null, null, null, null, null, null, null, null, null, null, null
                    ),
                    activity.shopId(), activity.id()
            );
            if (existing != null) {
                jdbcTemplate.update(
                        """
                                UPDATE sk_activity
                                SET activity_name = ?, description = ?, starts_at = ?, ends_at = ?,
                                    request_id = ?, trace_id = ?, updated_at = ?
                                WHERE shop_id = ? AND id = ? AND deleted = 0
                                """,
                        activity.activityName(), activity.description(),
                        activity.startsAt(), activity.endsAt(),
                        activity.requestId(), activity.traceId(), LocalDateTime.now(),
                        activity.shopId(), activity.id()
                );
                jdbcTemplate.update(
                        "DELETE FROM sk_activity_sku WHERE shop_id = ? AND activity_id = ?",
                        activity.shopId(), activity.id()
                );
                insertSkus(activity.shopId(), activity.id(), skus);
                return activity.id();
            }
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO sk_activity (
                                shop_id, activity_name, description, status, starts_at, ends_at,
                                request_id, trace_id, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, activity.shopId());
            statement.setString(2, activity.activityName());
            statement.setString(3, activity.description());
            statement.setString(4, activity.status().value());
            statement.setTimestamp(5, java.sql.Timestamp.valueOf(activity.startsAt()));
            statement.setTimestamp(6, java.sql.Timestamp.valueOf(activity.endsAt()));
            statement.setString(7, activity.requestId());
            statement.setString(8, activity.traceId());
            statement.setTimestamp(9, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Activity insert did not return a generated id");
        }
        Long activityId = key.longValue();
        insertSkus(activity.shopId(), activityId, skus);
        return activityId;
    }

    @Override
    @Transactional
    public int updateActivityStatus(Long shopId, Long activityId, SeckillActivityStatus currentStatus, SeckillActivityStatus newStatus) {
        return jdbcTemplate.update(
                """
                        UPDATE sk_activity
                        SET status = ?, updated_at = ?
                        WHERE shop_id = ? AND id = ? AND status = ? AND deleted = 0
                        """,
                newStatus.value(), LocalDateTime.now(),
                shopId, activityId, currentStatus.value()
        );
    }

    @Override
    @Transactional
    public int upsertSku(Long shopId, Long activityId, SeckillActivitySku sku) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE sk_activity_sku
                        SET product_id = ?, product_name = ?, sku_code = ?, sku_name = ?,
                            price_cent = ?, seckill_price_cent = ?, available_stock = ?,
                            activity_stock = ?, sold_count = ?, request_id = ?,
                            updated_at = ?
                        WHERE shop_id = ? AND activity_id = ? AND sku_id = ? AND deleted = 0
                        """,
                sku.productId(), sku.productName(), sku.skuCode(), sku.skuName(),
                sku.priceCent(), sku.seckillPriceCent(), sku.availableStock(),
                sku.activityStock(), sku.soldCount(), sku.requestId(),
                LocalDateTime.now(),
                shopId, activityId, sku.skuId()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO sk_activity_sku (
                                shop_id, activity_id, product_id, product_name, sku_id, sku_code, sku_name,
                                price_cent, seckill_price_cent, available_stock, activity_stock, sold_count,
                                request_id, trace_id, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    shopId, activityId,
                    sku.productId(), sku.productName(), sku.skuId(), sku.skuCode(), sku.skuName(),
                    sku.priceCent(), sku.seckillPriceCent(), sku.availableStock(),
                    sku.activityStock(), sku.soldCount(),
                    sku.requestId(), null,
                    LocalDateTime.now(), LocalDateTime.now()
            );
        }
        return 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeckillActivitySku> findSkuByRequestId(Long shopId, Long activityId, String requestId) {
        List<SeckillActivitySku> skus = jdbcTemplate.query(
                """
                        SELECT id, activity_id, product_id, product_name, sku_id, sku_code, sku_name,
                               price_cent, seckill_price_cent, available_stock, activity_stock, sold_count, request_id
                        FROM sk_activity_sku
                        WHERE shop_id = ? AND activity_id = ? AND request_id = ? AND deleted = 0
                        """,
                SKU_ROW_MAPPER,
                shopId, activityId, requestId
        );
        return skus.isEmpty() ? Optional.empty() : Optional.of(skus.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StatusRequestRecord> findStatusRequestByRequestId(Long shopId, Long activityId, String requestId) {
        List<StatusRequestRecord> records = jdbcTemplate.query(
                """
                        SELECT shop_id, activity_id, request_id, target_status
                        FROM sk_activity_status_request
                        WHERE shop_id = ? AND activity_id = ? AND request_id = ? AND deleted = 0
                        """,
                STATUS_REQUEST_ROW_MAPPER,
                shopId, activityId, requestId
        );
        return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
    }

    @Override
    @Transactional
    public void saveStatusRequest(Long shopId, Long activityId, String requestId, SeckillActivityStatus targetStatus, String traceId) {
        jdbcTemplate.update(
                """
                        INSERT INTO sk_activity_status_request (
                            shop_id, activity_id, request_id, target_status, trace_id, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                shopId, activityId, requestId, targetStatus.value(), traceId,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private List<SeckillActivitySku> findSkusByActivityId(Long shopId, Long activityId) {
        return jdbcTemplate.query(
                """
                        SELECT id, activity_id, product_id, product_name, sku_id, sku_code, sku_name,
                               price_cent, seckill_price_cent, available_stock, activity_stock, sold_count, request_id
                        FROM sk_activity_sku
                        WHERE shop_id = ? AND activity_id = ? AND deleted = 0
                        ORDER BY id ASC
                        """,
                SKU_ROW_MAPPER,
                shopId, activityId
        );
    }

    private void insertSkus(Long shopId, Long activityId, List<SeckillActivitySku> skus) {
        if (skus == null || skus.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO sk_activity_sku (
                            shop_id, activity_id, product_id, product_name, sku_id, sku_code, sku_name,
                            price_cent, seckill_price_cent, available_stock, activity_stock, sold_count,
                            request_id, trace_id, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                skus,
                skus.size(),
                (PreparedStatement statement, SeckillActivitySku s) -> {
                    statement.setLong(1, shopId);
                    statement.setLong(2, activityId);
                    statement.setLong(3, s.productId());
                    statement.setString(4, s.productName());
                    statement.setLong(5, s.skuId());
                    statement.setString(6, s.skuCode());
                    statement.setString(7, s.skuName());
                    statement.setLong(8, s.priceCent());
                    statement.setLong(9, s.seckillPriceCent());
                    statement.setLong(10, s.availableStock());
                    statement.setLong(11, s.activityStock());
                    statement.setLong(12, s.soldCount());
                    statement.setString(13, s.requestId());
                    statement.setString(14, null);
                    statement.setTimestamp(15, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                    statement.setTimestamp(16, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                }
        );
    }
}
