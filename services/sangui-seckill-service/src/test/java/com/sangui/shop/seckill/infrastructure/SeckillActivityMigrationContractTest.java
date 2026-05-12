package com.sangui.shop.seckill.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SeckillActivityMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V1__create_seckill_activity_tables.sql";

    @Test
    void migrationResourceExistsAndCreatesActivityAndSkuTables() throws IOException {
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

        assertThat(sql).contains("create table sk_activity");
        assertThat(sql).contains("create table sk_activity_sku");
        assertThat(sql).contains("create table sk_activity_status_request");

        assertThat(sql).contains("shop_id bigint not null default 1");
        assertThat(sql).contains("request_id varchar(64)");
        assertThat(sql).contains("status varchar(32)");
        assertThat(sql).contains("created_at datetime not null default current_timestamp");
        assertThat(sql).contains("updated_at datetime not null default current_timestamp");
        assertThat(sql).contains("deleted tinyint not null default 0");
        assertThat(sql).contains("version int not null default 0");

        assertThat(sql).contains("uk_sk_activity_shop_request");
        assertThat(sql).contains("idx_sk_activity_shop_status_created");
        assertThat(sql).contains("uk_sk_activity_sku_binding");
        assertThat(sql).contains("idx_sk_activity_sku_shop_activity");
        assertThat(sql).contains("uk_sk_activity_status_request");
    }
}
