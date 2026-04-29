package com.sangui.shop.common.mq;

public final class MqTopics {

    public static final String SECKILL_ORDER_REQUESTED = "sangui.seckill.order.requested";
    public static final String ORDER_CREATED = "sangui.order.created";
    public static final String PAYMENT_PAID = "sangui.payment.paid";
    public static final String ORDER_TIMEOUT_CANCELLED = "sangui.order.timeout.cancelled";
    public static final String AI_KNOWLEDGE_IMPORTED = "sangui.ai.knowledge.imported";

    private MqTopics() {
    }
}
