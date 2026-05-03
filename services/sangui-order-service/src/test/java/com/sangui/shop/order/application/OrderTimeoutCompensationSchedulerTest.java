package com.sangui.shop.order.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sangui.shop.order.client.dto.CancelExpiredOrdersResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderTimeoutCompensationSchedulerTest {

    @Test
    void runInvokesTimeoutCancelServiceWhenEnabled() {
        OrderTimeoutCancelService orderTimeoutCancelService = Mockito.mock(OrderTimeoutCancelService.class);
        when(orderTimeoutCancelService.cancelExpiredOrders(any(), any()))
                .thenReturn(new CancelExpiredOrdersResponse(1L, 2, 1, 0, 1));
        OrderTimeoutCompensationScheduler scheduler = new OrderTimeoutCompensationScheduler(
                orderTimeoutCancelService,
                true,
                1L,
                15,
                100
        );

        scheduler.run();

        verify(orderTimeoutCancelService).cancelExpiredOrders(any(), any());
    }

    @Test
    void runSkipsWhenDisabled() {
        OrderTimeoutCancelService orderTimeoutCancelService = Mockito.mock(OrderTimeoutCancelService.class);
        OrderTimeoutCompensationScheduler scheduler = new OrderTimeoutCompensationScheduler(
                orderTimeoutCancelService,
                false,
                1L,
                15,
                100
        );

        scheduler.run();

        verify(orderTimeoutCancelService, never()).cancelExpiredOrders(any(), any());
    }
}
