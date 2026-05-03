package com.sangui.shop.payment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentReconcileSchedulerTest {

    @Test
    void runInvokesReconcileServiceWhenEnabled() {
        PaymentReconcileService paymentReconcileService = Mockito.mock(PaymentReconcileService.class);
        when(paymentReconcileService.reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString()))
                .thenReturn(new PaymentReconcileResult(1L, 2, 1, 0, 1));
        PaymentReconcileScheduler scheduler = new PaymentReconcileScheduler(
                paymentReconcileService,
                true,
                1L,
                1,
                100
        );

        scheduler.run();

        verify(paymentReconcileService).reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void runSkipsWhenDisabled() {
        PaymentReconcileService paymentReconcileService = Mockito.mock(PaymentReconcileService.class);
        PaymentReconcileScheduler scheduler = new PaymentReconcileScheduler(
                paymentReconcileService,
                false,
                1L,
                1,
                100
        );

        scheduler.run();

        verify(paymentReconcileService, never()).reconcileCreatedPayments(anyLong(), anyInt(), anyInt(), anyString());
    }
}
