package com.sangui.shop.common.mq;

public final class MqEventTypes {

    public static final String SECKILL_ORDER_REQUESTED = "SECKILL_ORDER_REQUESTED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String PAYMENT_PAID = "PAYMENT_PAID";
    public static final String ORDER_TIMEOUT_CANCELLED = "ORDER_TIMEOUT_CANCELLED";
    public static final String AI_KNOWLEDGE_IMPORTED = "AI_KNOWLEDGE_IMPORTED";

    private MqEventTypes() {
    }
}
