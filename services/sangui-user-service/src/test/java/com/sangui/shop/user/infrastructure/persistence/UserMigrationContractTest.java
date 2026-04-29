package com.sangui.shop.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class UserMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V1__create_user_identity_tables.sql";

    @Test
    void userMigrationUsesFlywayNamingAndCreatesUserTableContract() throws IOException {
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

        assertThat(sql).contains("create table ums_user");
        assertThat(sql).contains("id bigint");
        assertThat(sql).contains("shop_id bigint not null default 1");
        assertThat(sql).contains("created_at datetime not null");
        assertThat(sql).contains("updated_at datetime not null");
        assertThat(sql).contains("deleted tinyint not null default 0");
        assertThat(sql).contains("version int not null default 0");
        assertThat(sql).contains("username varchar(64)");
        assertThat(sql).contains("mobile varchar(32)");
        assertThat(sql).contains("password_hash varchar(255)");
        assertThat(sql).contains("uk_ums_user_shop_username");
        assertThat(sql).contains("uk_ums_user_shop_mobile");
        assertThat(sql).contains("idx_ums_user_shop_deleted");
    }
}
