package com.sangui.shop.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrderReviewMerchantReplyMigrationContractTest {

    private static final String MIGRATION_RESOURCE = "db/migration/V10__add_order_review_merchant_reply.sql";

    @Test
    void reviewReplyMigrationAddsLatestReplySnapshotColumnsAndIndex() throws IOException {
        URL migration = Thread.currentThread().getContextClassLoader().getResource(MIGRATION_RESOURCE);

        assertThat(migration).isNotNull();

        String sql;
        try (InputStream input = migration.openStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }

        assertThat(sql).contains("alter table oms_order_review");
        assertThat(sql).contains("reply_content varchar(300)");
        assertThat(sql).contains("reply_visibility_status varchar(16) not null default 'visible'");
        assertThat(sql).contains("reply_request_id varchar(64)");
        assertThat(sql).contains("reply_operator varchar(64)");
        assertThat(sql).contains("reply_trace_id varchar(64)");
        assertThat(sql).contains("reply_updated_at datetime");
        assertThat(sql).contains("idx_oms_order_review_shop_reply_visibility_updated");
    }

    @Test
    void publicProductReviewSqlOnlyExposesVisibleReplies() throws IOException {
        String javaSource = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/sangui/shop/order/infrastructure/persistence/JdbcOrderRepository.java"),
                StandardCharsets.UTF_8
        ).toLowerCase(Locale.ROOT);

        assertThat(javaSource).contains("coalesce(r.reply_visibility_status, 'visible') = 'visible'");
        assertThat(javaSource).contains("then r.reply_content");
        assertThat(javaSource).contains("else null");
    }
}
