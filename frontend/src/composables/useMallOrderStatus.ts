import { computed, ref } from 'vue'
import {
  cancelOrder as cancelOrderApi,
  getOrder as getOrderApi,
  listOrders as listOrdersApi,
} from '../services/orderApi'
import { getPayment as getPaymentApi } from '../services/paymentApi'
import type { OrderPageResponse, OrderResponse } from '../types/api/order'
import type { PaymentResponse } from '../types/api/payment'
import {
  canCancelOrder,
  describeMallApiError,
  describePaymentStatus,
} from '../views/mall/mallCheckoutModel'

interface UseMallOrderStatusOptions {
  getOrder?: (orderId: number) => Promise<OrderResponse>
  listOrders?: (params: { page?: number, size?: number }) => Promise<OrderPageResponse>
  cancelOrder?: (orderId: number) => Promise<OrderResponse>
  getPayment?: (paymentNo: string) => Promise<PaymentResponse>
}

export function useMallOrderStatus(options: UseMallOrderStatusOptions = {}) {
  const order = ref<OrderResponse | null>(null)
  const orders = ref<OrderResponse[]>([])
  const payment = ref<PaymentResponse | null>(null)
  const paymentNo = ref('')
  const total = ref(0)
  const page = ref(1)
  const size = ref(5)
  const errorMessage = ref('')
  const isLoadingOrder = ref(false)
  const isLoadingOrders = ref(false)
  const isRefreshingPayment = ref(false)
  const isCancelling = ref(false)

  const canCancel = computed(() => canCancelOrder(order.value) && !isCancelling.value)
  const paymentStatus = computed(() => describePaymentStatus(order.value, payment.value))

  async function loadOrder(orderId: number, nextPaymentNo = paymentNo.value): Promise<OrderResponse | null> {
    isLoadingOrder.value = true
    errorMessage.value = ''

    try {
      const response = await (options.getOrder ?? defaultGetOrder)(orderId)
      order.value = response
      if (nextPaymentNo) {
        paymentNo.value = nextPaymentNo
        await refreshPayment(nextPaymentNo)
      } else {
        payment.value = null
      }
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      order.value = null
      payment.value = null
      return null
    } finally {
      isLoadingOrder.value = false
    }
  }

  async function loadOrders(nextPage = page.value): Promise<OrderPageResponse | null> {
    isLoadingOrders.value = true
    errorMessage.value = ''

    try {
      const response = await (options.listOrders ?? defaultListOrders)({ page: nextPage, size: size.value })
      orders.value = response.items
      total.value = response.total
      page.value = response.page
      size.value = response.size
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      orders.value = []
      total.value = 0
      return null
    } finally {
      isLoadingOrders.value = false
    }
  }

  async function refreshPayment(nextPaymentNo = paymentNo.value): Promise<PaymentResponse | null> {
    if (!nextPaymentNo || isRefreshingPayment.value) {
      return null
    }

    isRefreshingPayment.value = true
    errorMessage.value = ''

    try {
      const response = await (options.getPayment ?? defaultGetPayment)(nextPaymentNo)
      paymentNo.value = nextPaymentNo
      payment.value = response
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isRefreshingPayment.value = false
    }
  }

  async function cancelCurrentOrder(): Promise<OrderResponse | null> {
    if (!order.value || !canCancel.value) {
      return null
    }

    isCancelling.value = true
    errorMessage.value = ''

    try {
      const response = await (options.cancelOrder ?? defaultCancelOrder)(order.value.orderId)
      order.value = response
      payment.value = null
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isCancelling.value = false
    }
  }

  function acceptCreatedOrder(createdOrder: OrderResponse) {
    order.value = createdOrder
    payment.value = null
    paymentNo.value = ''
  }

  function acceptPayment(createdPayment: PaymentResponse) {
    payment.value = createdPayment
    paymentNo.value = createdPayment.paymentNo
    if (order.value && order.value.orderId === createdPayment.orderId) {
      order.value = {
        ...order.value,
        status: createdPayment.status === 'paid' ? 'paid' : order.value.status,
      }
    }
  }

  return {
    order,
    orders,
    payment,
    paymentNo,
    total,
    page,
    size,
    errorMessage,
    isLoadingOrder,
    isLoadingOrders,
    isRefreshingPayment,
    isCancelling,
    canCancel,
    paymentStatus,
    loadOrder,
    loadOrders,
    refreshPayment,
    cancelCurrentOrder,
    acceptCreatedOrder,
    acceptPayment,
  }
}

async function defaultGetOrder(orderId: number): Promise<OrderResponse> {
  const result = await getOrderApi(orderId)
  return result.data
}

async function defaultListOrders(params: { page?: number, size?: number }): Promise<OrderPageResponse> {
  const result = await listOrdersApi(params)
  return result.data
}

async function defaultCancelOrder(orderId: number): Promise<OrderResponse> {
  const result = await cancelOrderApi(orderId)
  return result.data
}

async function defaultGetPayment(paymentNo: string): Promise<PaymentResponse> {
  const result = await getPaymentApi(paymentNo)
  return result.data
}
