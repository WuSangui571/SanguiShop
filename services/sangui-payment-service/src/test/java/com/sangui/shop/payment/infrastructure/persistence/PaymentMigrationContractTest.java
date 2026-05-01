package com.sangui.shop.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V1__create_payment_tables.sql";

    @Test
    void paymentMigrationUsesFlywayNamingAndCreatesPaymentTablesContract() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration)
                .as("Flyway migration must be available from classpath:db/migration")
                .isNotNull();
        assertThat(migration.toString())
                .as("Flyway migration filename must remain stable")
                .contains(MIGRATION_RESOURCE);

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("create table pay_payment_order");
        assertThat(sql).contains("create table pay_callback_log");
        assertThat(sql).contains("shop_id bigint not null default 1");
        assertThat(sql).contains("payment_no varchar(64) not null");
        assertThat(sql).contains("channel varchar(32) not null");
        assertThat(sql).contains("amount_cent bigint not null");
        assertThat(sql).contains("status varchar(32) not null default 'created'");
        assertThat(sql).contains("uk_pay_payment_order_shop_payment_no");
        assertThat(sql).contains("idx_pay_payment_order_shop_order_id");
        assertThat(sql).contains("idx_pay_payment_order_shop_user_id");
        assertThat(sql).contains("uk_pay_callback_log_channel_trade_no");
        assertThat(sql).contains("idx_pay_callback_log_shop_payment_no");
    }
}
