import { computed, ref } from 'vue'
import {
  cancelOrder as cancelOrderApi,
  confirmOrderReceipt as confirmOrderReceiptApi,
  createOrderReview as createOrderReviewApi,
  getOrder as getOrderApi,
  listOrders as listOrdersApi,
} from '../services/orderApi'
import { createPayment as createPaymentApi, getPayment as getPaymentApi } from '../services/paymentApi'
import type { MallSession } from '../types/api/auth'
import type {
  ConfirmOrderReceiptRequest,
  CreateOrderReviewRequest,
  OrderPageResponse,
  OrderResponse,
  OrderReviewResponse,
} from '../types/api/order'
import type { CreatePaymentRequest, PaymentResponse } from '../types/api/payment'
import {
  buildCreatePaymentRequest,
  canCancelOrder,
  classifyMallPaymentFailure,
  createOrderReviewRequestId,
  createReceiptConfirmationRequestId,
  createPaymentNo,
  describeMallApiError,
  describePaymentStatus,
  type MallPaymentFailure,
} from '../views/mall/mallCheckoutModel'
import {
  applyMallPaymentToOrder,
  applyMallPaymentToOrderList,
  applyMallReviewToOrder,
  applyMallReviewToOrderList,
  mergeOrderIntoList,
  upsertOrderIntoList,
} from '../views/mall/mallOrderStatusModel'

interface UseMallOrderStatusOptions {
  getOrder?: (orderId: number) => Promise<OrderResponse>
  listOrders?: (params: { page?: number, size?: number }) => Promise<OrderPageResponse>
  cancelOrder?: (orderId: number) => Promise<OrderResponse>
  confirmOrderReceipt?: (orderId: number, payload: ConfirmOrderReceiptRequest) => Promise<OrderResponse>
  createOrderReview?: (orderId: number, payload: CreateOrderReviewRequest) => Promise<OrderReviewResponse>
  createPayment?: (payload: CreatePaymentRequest) => Promise<PaymentResponse>
  getPayment?: (paymentNo: string) => Promise<PaymentResponse>
  createPaymentNo?: () => string
  createReceiptConfirmationRequestId?: () => string
  createOrderReviewRequestId?: () => string
}

type OrderRefreshResult = 'idle' | 'success' | 'error'

export function useMallOrderStatus(options: UseMallOrderStatusOptions = {}) {
  const order = ref<OrderResponse | null>(null)
  const orders = ref<OrderResponse[]>([])
  const payment = ref<PaymentResponse | null>(null)
  const paymentNo = ref('')
  const paymentFailure = ref<MallPaymentFailure | null>(null)
  const total = ref(0)
  const page = ref(1)
  const size = ref(5)
  const errorMessage = ref('')
  const isLoadingOrder = ref(false)
  const isLoadingOrders = ref(false)
  const isRefreshingPayment = ref(false)
  const isCancelling = ref(false)
  const isConfirmingReceipt = ref(false)
  const isSubmittingReview = ref(false)
  const isSubmittingPayment = ref(false)
  const orderRefreshResult = ref<OrderRefreshResult>('idle')

  const canCancel = computed(() => canCancelOrder(order.value) && !isCancelling.value)
  const canPay = computed(() => order.value?.status === 'created' && !payment.value && !isSubmittingPayment.value)
  const canConfirmReceipt = computed(() => isShippedOrder(order.value) && !isConfirmingReceipt.value)
  const canReview = computed(() => isReviewableCompletedOrder(order.value) && !isSubmittingReview.value)
  const paymentStatus = computed(() => describePaymentStatus(order.value, payment.value))

  async function loadOrder(
    orderId: number,
    nextPaymentNo = paymentNo.value,
    loadOptions: { refreshPayment?: boolean } = {},
  ): Promise<OrderResponse | null> {
    isLoadingOrder.value = true
    errorMessage.value = ''
    paymentFailure.value = null
    orderRefreshResult.value = 'idle'
    const shouldPreserveExistingOrder = order.value?.orderId === orderId
    const shouldRefreshPayment = loadOptions.refreshPayment ?? Boolean(nextPaymentNo)

    try {
      const response = await (options.getOrder ?? defaultGetOrder)(orderId)
      order.value = response
      orders.value = loadOptions.refreshPayment === false
        ? mergeOrderIntoList(orders.value, response)
        : upsertOrderIntoList(orders.value, response)
      if (nextPaymentNo) {
        paymentNo.value = nextPaymentNo
        if (shouldRefreshPayment) {
          await refreshPayment(nextPaymentNo)
        }
      } else {
        paymentNo.value = ''
        payment.value = null
        paymentFailure.value = null
      }
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      if (!shouldPreserveExistingOrder) {
        order.value = null
        payment.value = null
      }
      return null
    } finally {
      isLoadingOrder.value = false
    }
  }

  async function refreshCurrentOrder(): Promise<OrderResponse | null> {
    if (!order.value || isLoadingOrder.value) {
      return null
    }

    const response = await loadOrder(order.value.orderId, paymentNo.value, {
      refreshPayment: false,
    })
    orderRefreshResult.value = response ? 'success' : 'error'
    return response
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
    paymentFailure.value = null

    try {
      const response = await (options.getPayment ?? defaultGetPayment)(nextPaymentNo)
      paymentNo.value = nextPaymentNo
      payment.value = response
      paymentFailure.value = null
      order.value = applyMallPaymentToOrder(order.value, response)
      orders.value = applyMallPaymentToOrderList(orders.value, response)
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
      orders.value = mergeOrderIntoList(orders.value, response)
      payment.value = null
      paymentNo.value = ''
      paymentFailure.value = null
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isCancelling.value = false
    }
  }

  async function confirmCurrentOrderReceipt(): Promise<OrderResponse | null> {
    if (!order.value || !canConfirmReceipt.value) {
      return null
    }

    isConfirmingReceipt.value = true
    errorMessage.value = ''

    try {
      const response = await (options.confirmOrderReceipt ?? defaultConfirmOrderReceipt)(
        order.value.orderId,
        {
          requestId: (options.createReceiptConfirmationRequestId ?? createReceiptConfirmationRequestId)(),
        },
      )
      order.value = response
      orders.value = mergeOrderIntoList(orders.value, response)
      paymentFailure.value = null
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isConfirmingReceipt.value = false
    }
  }

  async function submitCurrentOrderReview(input: { rating: number, content?: string | null }): Promise<OrderReviewResponse | null> {
    if (!order.value || !canReview.value) {
      return null
    }

    isSubmittingReview.value = true
    errorMessage.value = ''

    try {
      const response = await (options.createOrderReview ?? defaultCreateOrderReview)(
        order.value.orderId,
        {
          requestId: (options.createOrderReviewRequestId ?? createOrderReviewRequestId)(),
          rating: input.rating,
          content: input.content ?? null,
          imageUrls: [],
        },
      )
      order.value = applyMallReviewToOrder(order.value, response)
      orders.value = applyMallReviewToOrderList(orders.value, response)
      paymentFailure.value = null
      return response
    } catch (caught) {
      errorMessage.value = describeMallApiError(caught)
      return null
    } finally {
      isSubmittingReview.value = false
    }
  }

  function acceptCreatedOrder(createdOrder: OrderResponse) {
    order.value = createdOrder
    orders.value = upsertOrderIntoList(orders.value, createdOrder)
    payment.value = null
    paymentNo.value = ''
    paymentFailure.value = null
  }

  function acceptPayment(createdPayment: PaymentResponse) {
    payment.value = createdPayment
    paymentNo.value = createdPayment.paymentNo
    paymentFailure.value = null
    const updatedOrder = applyMallPaymentToOrder(order.value, createdPayment)
    if (updatedOrder) {
      order.value = updatedOrder
      orders.value = mergeOrderIntoList(orders.value, order.value)
    }
  }

  async function submitPayment(session: MallSession): Promise<PaymentResponse | null> {
    if (!order.value || !canPay.value) {
      return null
    }

    isSubmittingPayment.value = true
    errorMessage.value = ''
    paymentFailure.value = null

    try {
      const createPayment = options.createPayment ?? defaultCreatePayment
      const nextPaymentNo = paymentNo.value || (options.createPaymentNo ?? createPaymentNo)()
      paymentNo.value = nextPaymentNo
      const response = await createPayment(buildCreatePaymentRequest({
        session,
        orderId: order.value.orderId,
        paymentNo: nextPaymentNo,
      }))
      acceptPayment(response)
      return response
    } catch (caught) {
      const failure = classifyMallPaymentFailure(caught)
      paymentFailure.value = failure
      errorMessage.value = failure.detail
      return null
    } finally {
      isSubmittingPayment.value = false
    }
  }

  return {
    order,
    orders,
    payment,
    paymentNo,
    paymentFailure,
    total,
    page,
    size,
    errorMessage,
    isLoadingOrder,
    isLoadingOrders,
    isRefreshingPayment,
    isCancelling,
    isConfirmingReceipt,
    isSubmittingReview,
    isSubmittingPayment,
    orderRefreshResult,
    canCancel,
    canPay,
    canConfirmReceipt,
    canReview,
    paymentStatus,
    loadOrder,
    refreshCurrentOrder,
    loadOrders,
    refreshPayment,
    cancelCurrentOrder,
    confirmCurrentOrderReceipt,
    submitCurrentOrderReview,
    submitPayment,
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

async function defaultConfirmOrderReceipt(
  orderId: number,
  payload: ConfirmOrderReceiptRequest,
): Promise<OrderResponse> {
  const result = await confirmOrderReceiptApi(orderId, payload)
  return result.data
}

async function defaultCreateOrderReview(
  orderId: number,
  payload: CreateOrderReviewRequest,
): Promise<OrderReviewResponse> {
  const result = await createOrderReviewApi(orderId, payload)
  return result.data
}

async function defaultGetPayment(paymentNo: string): Promise<PaymentResponse> {
  const result = await getPaymentApi(paymentNo)
  return result.data
}

async function defaultCreatePayment(payload: CreatePaymentRequest): Promise<PaymentResponse> {
  const result = await createPaymentApi(payload)
  return result.data
}

function isShippedOrder(order: OrderResponse | null): boolean {
  return order?.status === 'shipped' || order?.fulfillmentStatus === 'shipped'
}

function isReviewableCompletedOrder(order: OrderResponse | null): boolean {
  if (!order || (order.status !== 'completed' && order.fulfillmentStatus !== 'completed')) {
    return false
  }
  return order.reviewed !== true && !order.review
}
