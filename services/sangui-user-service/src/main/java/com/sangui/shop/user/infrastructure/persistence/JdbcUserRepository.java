package com.sangui.shop.user.infrastructure.persistence;

import com.sangui.shop.user.domain.UserAccount;
import com.sangui.shop.user.domain.UserRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final RowMapper<UserAccount> USER_ROW_MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getLong("id"),
            rs.getLong("shop_id"),
            rs.getString("username"),
            rs.getString("mobile"),
            rs.getString("password_hash"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findById(Long userId) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, username, mobile, password_hash, status
                        FROM ums_user
                        WHERE id = ? AND deleted = 0
                        LIMIT 1
                        """,
                USER_ROW_MAPPER,
                userId
        ).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByUsername(Long shopId, String username) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, username, mobile, password_hash, status
                        FROM ums_user
                        WHERE shop_id = ? AND username = ? AND deleted = 0
                        LIMIT 1
                        """,
                USER_ROW_MAPPER,
                shopId,
                username
        ).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByMobile(Long shopId, String mobile) {
        return jdbcTemplate.query(
                """
                        SELECT id, shop_id, username, mobile, password_hash, status
                        FROM ums_user
                        WHERE shop_id = ? AND mobile = ? AND deleted = 0
                        LIMIT 1
                        """,
                USER_ROW_MAPPER,
                shopId,
                mobile
        ).stream().findFirst();
    }

    @Override
    public Long save(Long shopId, String username, String mobile, String passwordHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO ums_user (shop_id, username, mobile, password_hash, status)
                            VALUES (?, ?, ?, ?, 'ACTIVE')
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, shopId);
            statement.setString(2, username);
            statement.setString(3, mobile);
            statement.setString(4, passwordHash);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("User insert did not return a generated id");
        }
        return key.longValue();
    }

    @Override
    public void markLoginSuccess(Long userId) {
        jdbcTemplate.update(
                "UPDATE ums_user SET last_login_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted = 0",
                userId
        );
    }
}
