package com.sangui.shop.common.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEnvelopeJsonTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void serializesMqEventEnvelopeFields() throws Exception {
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
                "evt_20260429_000001",
                "SECKILL_ORDER_REQUESTED",
                1,
                OffsetDateTime.parse("2026-04-29T15:00:00+08:00"),
                1L,
                "trace-001",
                Map.of("skuId", 200L)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(envelope));

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("eventId", "eventType", "version", "occurredAt", "shopId", "traceId", "payload");
        assertThat(json.get("eventId").asText()).isEqualTo("evt_20260429_000001");
        assertThat(json.get("eventType").asText()).isEqualTo("SECKILL_ORDER_REQUESTED");
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-04-29T15:00:00+08:00");
        assertThat(json.get("shopId").asLong()).isEqualTo(1L);
        assertThat(json.get("traceId").asText()).isEqualTo("trace-001");
        assertThat(json.get("payload").get("skuId").asLong()).isEqualTo(200L);
    }
}
