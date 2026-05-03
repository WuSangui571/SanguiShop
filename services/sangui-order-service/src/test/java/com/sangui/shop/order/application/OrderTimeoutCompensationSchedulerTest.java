package com.sangui.shop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderTimeoutCompensationSchedulerTest {

    @Test
    void runInvokesTimeoutCancelServiceWhenEnabledAndRecordsMetrics() {
        OrderTimeoutCancelService orderTimeoutCancelService = Mockito.mock(OrderTimeoutCancelService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(orderTimeoutCancelService.cancelExpiredOrders(any(), any()))
                .thenReturn(new CancelExpiredOrdersResponse(1L, 2, 1, 0, 1));
        OrderTimeoutCompensationScheduler scheduler = new OrderTimeoutCompensationScheduler(
                orderTimeoutCancelService,
                meterRegistry,
                true,
                1L,
                15,
                100
        );

        scheduler.run();

        verify(orderTimeoutCancelService).cancelExpiredOrders(any(), any());
        assertThat(counterValue(meterRegistry, "success")).isEqualTo(1.0);
        assertThat(itemCounterValue(meterRegistry, "scanned")).isEqualTo(2.0);
        assertThat(itemCounterValue(meterRegistry, "cancelled")).isEqualTo(1.0);
        assertThat(itemCounterValue(meterRegistry, "failed")).isEqualTo(1.0);
    }

    @Test
    void runSkipsWhenDisabledAndRecordsDisabledMetric() {
        OrderTimeoutCancelService orderTimeoutCancelService = Mockito.mock(OrderTimeoutCancelService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        OrderTimeoutCompensationScheduler scheduler = new OrderTimeoutCompensationScheduler(
                orderTimeoutCancelService,
                meterRegistry,
                false,
                1L,
                15,
                100
        );

        scheduler.run();

        verify(orderTimeoutCancelService, never()).cancelExpiredOrders(any(), any());
        assertThat(counterValue(meterRegistry, "disabled")).isEqualTo(1.0);
    }

    @Test
    void runRecordsFailedMetricWhenBatchThrows() {
        OrderTimeoutCancelService orderTimeoutCancelService = Mockito.mock(OrderTimeoutCancelService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(orderTimeoutCancelService.cancelExpiredOrders(any(), any()))
                .thenThrow(new RuntimeException("boom"));
        OrderTimeoutCompensationScheduler scheduler = new OrderTimeoutCompensationScheduler(
                orderTimeoutCancelService,
                meterRegistry,
                true,
                1L,
                15,
                100
        );

        scheduler.run();

        assertThat(counterValue(meterRegistry, "failed")).isEqualTo(1.0);
    }

    private double counterValue(MeterRegistry meterRegistry, String result) {
        return meterRegistry
                .find("sangui.compensation.job.run.total")
                .tags("service", "order", "job", "order-timeout", "result", result)
                .counter()
                .count();
    }

    private double itemCounterValue(MeterRegistry meterRegistry, String result) {
        return meterRegistry
                .find("sangui.compensation.job.item.total")
                .tags("service", "order", "job", "order-timeout", "result", result)
                .counter()
                .count();
    }
}
