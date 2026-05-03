package com.sangui.shop.payment.infrastructure.persistence;

import com.sangui.shop.payment.domain.PaymentCallbackLogDraft;
import com.sangui.shop.payment.domain.PaymentCallbackLogRecord;
import com.sangui.shop.payment.domain.PaymentCreateDraft;
import com.sangui.shop.payment.domain.PaymentOrderRecord;
import com.sangui.shop.payment.domain.PaymentRepository;
import com.sangui.shop.payment.domain.PaymentStatus;
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

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private static final RowMapper<PaymentOrderRecord> PAYMENT_ROW_MAPPER = (rs, rowNum) -> new PaymentOrderRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getString("user_id"),
            rs.getString("reservation_no"),
            rs.getString("payment_no"),
            rs.getString("channel"),
            rs.getLong("amount_cent"),
            PaymentStatus.fromValue(rs.getString("status")),
            rs.getString("trace_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getString("last_compensation_result"),
            rs.getString("last_compensation_error_code"),
            rs.getString("last_compensation_reason"),
            rs.getString("last_compensation_trace_id"),
            rs.getString("last_compensation_trigger"),
            rs.getString("last_compensation_operator"),
            rs.getTimestamp("last_compensated_at") == null ? null : rs.getTimestamp("last_compensated_at").toLocalDateTime()
    );

    private static final RowMapper<PaymentCallbackLogRecord> CALLBACK_LOG_ROW_MAPPER = (rs, rowNum) -> new PaymentCallbackLogRecord(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getString("payment_no"),
            rs.getString("channel"),
            rs.getString("channel_trade_no"),
            rs.getString("callback_type"),
            rs.getString("process_status"),
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
                        SELECT id, shop_id, order_id, order_no, user_id, reservation_no, payment_no, channel, amount_cent, status, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at
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
    public List<PaymentOrderRecord> findCreatedPayments(Long shopId, LocalDateTime createdBefore, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, reservation_no, payment_no, channel, amount_cent, status, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at
                        FROM pay_payment_order
                        WHERE shop_id = ? AND status = ? AND created_at <= ? AND deleted = 0
                        ORDER BY id ASC
                        LIMIT ?
                        """,
                PAYMENT_ROW_MAPPER,
                shopId,
                PaymentStatus.CREATED.value(),
                createdBefore,
                limit
        );
    }

    @Override
    public List<PaymentOrderRecord> findFailedPayments(Long shopId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, order_id, order_no, user_id, reservation_no, payment_no, channel, amount_cent, status, trace_id,
                               created_at, updated_at, last_compensation_result, last_compensation_error_code,
                               last_compensation_reason, last_compensation_trace_id, last_compensation_trigger,
                               last_compensation_operator, last_compensated_at
                        FROM pay_payment_order
                        WHERE shop_id = ? AND status = ? AND deleted = 0
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ?
                        """,
                PAYMENT_ROW_MAPPER,
                shopId,
                PaymentStatus.FAILED.value(),
                limit
        );
    }

    @Override
    public Optional<PaymentCallbackLogRecord> findCallbackLog(String channel, String channelTradeNo) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, payment_no, channel, channel_trade_no, callback_type, process_status, trace_id
                        FROM pay_callback_log
                        WHERE channel = ? AND channel_trade_no = ? AND deleted = 0
                        LIMIT 1
                        """,
                CALLBACK_LOG_ROW_MAPPER,
                channel,
                channelTradeNo
        ).stream().findFirst();
    }

    @Override
    public Long createPaymentOrder(PaymentCreateDraft draft, PaymentStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO pay_payment_order (
                                shop_id, order_id, order_no, user_id, reservation_no, payment_no, channel, amount_cent, trace_id, status
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, draft.shopId());
            statement.setLong(2, draft.orderId());
            statement.setString(3, draft.orderNo());
            statement.setString(4, draft.userId());
            statement.setString(5, draft.reservationNo());
            statement.setString(6, draft.paymentNo());
            statement.setString(7, draft.channel());
            statement.setLong(8, draft.amountCent());
            statement.setString(9, draft.traceId());
            statement.setString(10, status.value());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Payment insert did not return a generated id");
        }
        return key.longValue();
    }

    @Override
    public Long createCallbackLog(PaymentCallbackLogDraft draft) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO pay_callback_log (
                                shop_id, payment_no, channel, channel_trade_no, callback_type, payload_json, trace_id
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, draft.shopId());
            statement.setString(2, draft.paymentNo());
            statement.setString(3, draft.channel());
            statement.setString(4, draft.channelTradeNo());
            statement.setString(5, draft.callbackType());
            statement.setString(6, draft.payloadJson());
            statement.setString(7, draft.traceId());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Payment callback insert did not return a generated id");
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

    @Override
    public void updateCompensationMetadata(
            Long shopId,
            Long paymentId,
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
                        UPDATE pay_payment_order
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
                paymentId
        );
    }

    @Override
    public void appendCompensationAttempt(
            Long shopId,
            Long paymentId,
            Long orderId,
            String paymentNo,
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
                        INSERT INTO pay_payment_compensation_attempt (
                            shop_id, payment_id, order_id, payment_no, order_no, reservation_no,
                            result, error_code, reason, trace_id, trigger_type, operator
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                shopId,
                paymentId,
                orderId,
                paymentNo,
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

    @Override
    public void updateCallbackProcessStatus(Long callbackLogId, String processStatus) {
        jdbcTemplate.update(
                """
                        UPDATE pay_callback_log
                        SET process_status = ?
                        WHERE id = ? AND deleted = 0
                        """,
                processStatus,
                callbackLogId
        );
    }
}
