package com.sangui.shop.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PaymentCompensationOpsMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V4__add_payment_compensation_ops_columns.sql";

    @Test
    void paymentCompensationOpsMigrationAddsLatestCompensationColumns() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("last_compensation_result");
        assertThat(sql).contains("last_compensation_error_code");
        assertThat(sql).contains("last_compensation_reason");
        assertThat(sql).contains("last_compensation_trace_id");
        assertThat(sql).contains("last_compensation_trigger");
        assertThat(sql).contains("last_compensated_at");
    }
}
