package com.sangui.shop.order.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderCompensationMetricsRecorder {

    static final String JOB_NAME = "order-timeout";
    private static final String RUN_COUNTER = "sangui.compensation.job.run.total";
    private static final String ITEM_COUNTER = "sangui.compensation.job.item.total";

    private final MeterRegistry meterRegistry;

    public OrderCompensationMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementRun(String result) {
        Counter.builder(RUN_COUNTER)
                .description("Total compensation job runs grouped by result.")
                .tag("service", "order")
                .tag("job", JOB_NAME)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    public void incrementItem(String result, int amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(ITEM_COUNTER)
                .description("Total compensation job items grouped by result.")
                .tag("service", "order")
                .tag("job", JOB_NAME)
                .tag("result", result)
                .register(meterRegistry)
                .increment(amount);
    }
}
