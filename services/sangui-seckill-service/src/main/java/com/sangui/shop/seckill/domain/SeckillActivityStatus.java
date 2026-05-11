package com.sangui.shop.seckill.domain;

import java.util.Map;
import java.util.Set;

public enum SeckillActivityStatus {
    DRAFT("draft"),
    SCHEDULED("scheduled"),
    ACTIVE("active"),
    ENDED("ended");

    private static final Map<SeckillActivityStatus, Set<SeckillActivityStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(SCHEDULED),
            SCHEDULED, Set.of(ACTIVE),
            ACTIVE, Set.of(ENDED),
            ENDED, Set.of()
    );

    private final String value;

    SeckillActivityStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean canTransitionTo(SeckillActivityStatus target) {
        Set<SeckillActivityStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public static SeckillActivityStatus fromValue(String value) {
        for (SeckillActivityStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown seckill activity status: " + value);
    }
}
