package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V1__create_order_tables.sql";

    @Test
    void orderMigrationUsesFlywayNamingAndCreatesOrderTablesContract() throws IOException {
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

        assertThat(sql).contains("create table oms_order");
        assertThat(sql).contains("create table oms_order_item");
        assertThat(sql).contains("shop_id bigint not null default 1");
        assertThat(sql).contains("request_id varchar(64) not null");
        assertThat(sql).contains("trace_id varchar(64) null");
        assertThat(sql).contains("status varchar(32) not null default 'created'");
        assertThat(sql).contains("total_amount_cent bigint not null");
        assertThat(sql).contains("uk_oms_order_shop_order_no");
        assertThat(sql).contains("uk_oms_order_shop_user_request");
        assertThat(sql).contains("idx_oms_order_shop_user_id");
        assertThat(sql).contains("idx_oms_order_item_shop_order");
        assertThat(sql).contains("idx_oms_order_item_shop_sku");
        assertThat(sql).contains("foreign key (order_id) references oms_order (id)");
    }
}
