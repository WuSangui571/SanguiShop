package com.sangui.shop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentReconcileSchedulerTest {

    @Test
    void runInvokesReconcileServiceWhenEnabledAndRecordsMetrics() {
        PaymentReconcileService paymentReconcileService = Mockito.mock(PaymentReconcileService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(paymentReconcileService.reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString()))
                .thenReturn(new PaymentReconcileResult(1L, 2, 1, 0, 1));
        PaymentReconcileScheduler scheduler = new PaymentReconcileScheduler(
                paymentReconcileService,
                new PaymentCompensationMetricsRecorder(meterRegistry),
                true,
                1L,
                1,
                100
        );

        scheduler.run();

        verify(paymentReconcileService).reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString());
        assertThat(counterValue(meterRegistry, "success")).isEqualTo(1.0);
        assertThat(itemCounterValue(meterRegistry, "scanned")).isEqualTo(2.0);
        assertThat(itemCounterValue(meterRegistry, "settled")).isEqualTo(1.0);
        assertThat(itemCounterValue(meterRegistry, "failed")).isEqualTo(1.0);
    }

    @Test
    void runSkipsWhenDisabledAndRecordsDisabledMetric() {
        PaymentReconcileService paymentReconcileService = Mockito.mock(PaymentReconcileService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        PaymentReconcileScheduler scheduler = new PaymentReconcileScheduler(
                paymentReconcileService,
                new PaymentCompensationMetricsRecorder(meterRegistry),
                false,
                1L,
                1,
                100
        );

        scheduler.run();

        verify(paymentReconcileService, never()).reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString());
        assertThat(counterValue(meterRegistry, "disabled")).isEqualTo(1.0);
    }

    @Test
    void runRecordsFailedMetricWhenBatchThrows() {
        PaymentReconcileService paymentReconcileService = Mockito.mock(PaymentReconcileService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(paymentReconcileService.reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("boom"));
        PaymentReconcileScheduler scheduler = new PaymentReconcileScheduler(
                paymentReconcileService,
                new PaymentCompensationMetricsRecorder(meterRegistry),
                true,
                1L,
                1,
                100
        );

        scheduler.run();

        assertThat(counterValue(meterRegistry, "failed")).isEqualTo(1.0);
    }

    private double counterValue(MeterRegistry meterRegistry, String result) {
        return meterRegistry
                .find("sangui.compensation.job.run.total")
                .tags("service", "payment", "job", "payment-reconcile", "trigger", "scheduler", "result", result)
                .counter()
                .count();
    }

    private double itemCounterValue(MeterRegistry meterRegistry, String result) {
        return meterRegistry
                .find("sangui.compensation.job.item.total")
                .tags("service", "payment", "job", "payment-reconcile", "trigger", "scheduler", "result", result)
                .counter()
                .count();
    }
}
