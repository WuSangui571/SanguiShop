package com.sangui.shop.order.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderCompensationQueryResponseJsonTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void serializesAggregateHistoryQueryResponseFields() throws Exception {
        OrderCompensationRecordResponse order = new OrderCompensationRecordResponse(
                101L,
                "ORD-001",
                "10001",
                "ord:10001:req-001",
                "cancelled",
                59900L,
                "trace-order",
                OffsetDateTime.parse("2026-05-03T12:00:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "inventory release timeout",
                "trace-history",
                "scheduler",
                null,
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        OrderCompensationAttemptResponse attempt = new OrderCompensationAttemptResponse(
                1L,
                101L,
                "ORD-001",
                "ord:10001:req-001",
                "failed",
                "DOWNSTREAM_TIMEOUT",
                "inventory release timeout",
                "trace-history",
                "scheduler",
                null,
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                OffsetDateTime.parse("2026-05-03T12:10:00+08:00")
        );
        OrderCompensationQueryResponse response = new OrderCompensationQueryResponse(
                1L,
                1,
                20,
                1L,
                List.of(new OrderCompensationAggregateResponse(
                        order,
                        1L,
                        1L,
                        OffsetDateTime.parse("2026-05-03T12:10:00+08:00"),
                        List.of(attempt)
                ))
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("shopId", "pageNo", "pageSize", "total", "items");
        assertThat(json.get("items").get(0).fieldNames()).toIterable()
                .containsExactly("order", "matchedAttemptCount", "totalAttemptCount", "latestAttemptAt", "attempts");
        assertThat(json.get("items").get(0).get("order").get("orderNo").asText()).isEqualTo("ORD-001");
        assertThat(json.get("items").get(0).get("attempts").get(0).get("trigger").asText()).isEqualTo("scheduler");
    }
}
