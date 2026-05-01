package com.sangui.shop.payment.infrastructure.persistence;

import com.sangui.shop.payment.domain.PaymentCreateDraft;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private static final RowMapper<PaymentOrderRecord> PAYMENT_ROW_MAPPER = (rs, rowNum) -> new PaymentOrderRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getString("user_id"),
            rs.getString("payment_no"),
            rs.getString("channel"),
            rs.getLong("amount_cent"),
            PaymentStatus.fromValue(rs.getString("status")),
            rs.getString("trace_id")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PaymentOrderRecord> findByPaymentNo(Long shopId, String paymentNo) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, payment_no, channel, amount_cent, status, trace_id
                        FROM pay_payment_order
                        WHERE shop_id = ? AND payment_no = ? AND deleted = 0
                        LIMIT 1
                        """,
                PAYMENT_ROW_MAPPER,
                shopId,
                paymentNo
        ).stream().findFirst();
    }

    @Override
    public Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO pay_payment_order (
                                shop_id, order_id, order_no, user_id, payment_no, channel, amount_cent, trace_id, status
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, draft.shopId());
            statement.setLong(2, draft.orderId());
            statement.setString(3, draft.orderNo());
            statement.setString(4, draft.userId());
            statement.setString(5, draft.paymentNo());
            statement.setString(6, draft.channel());
            statement.setLong(7, draft.amountCent());
            statement.setString(8, draft.traceId());
            statement.setString(9, status.value());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Payment insert did not return a generated id");
        }
        return key.longValue();
    }

    @Override
    public void updatePaymentStatus(Long shopId, Long paymentId, PaymentStatus status) {
        jdbcTemplate.update(
                """
                        UPDATE pay_payment_order
                        SET status = ?
                        WHERE shop_id = ? AND id = ? AND deleted = 0
                        """,
                status.value(),
                shopId,
                paymentId
        );
    }
}
