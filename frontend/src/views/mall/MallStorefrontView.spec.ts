// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { computed, ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MallStorefrontView from './MallStorefrontView.vue'
import type { MallSession } from '../../types/api/auth'
import type { OrderResponse } from '../../types/api/order'
import type { PaymentResponse } from '../../types/api/payment'
import type { ProductListResponse, ProductReviewPageResponse } from '../../types/api/product'

const mallOrderStatusMock = vi.hoisted(() => ({
  current: undefined as unknown,
}))

const mallSession: MallSession = {
  userId: 10001,
  shopId: 1,
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresAt: '2099-12-31T23:59:59+08:00',
  roles: ['MALL_USER'],
}

vi.mock('../../composables/useAppPreferences', () => ({
  useAppPreferences: () => ({
    t: (key: string, params?: Record<string, string | number>) => {
      if (!params) {
        return key
      }
      const values = Object.entries(params)
        .map(([paramKey, value]) => `${paramKey}=${value}`)
        .join(',')
      return `${key}(${values})`
    },
    locale: { value: 'zh-Hans' },
    theme: { value: 'light' },
  }),
}))

vi.mock('../../composables/useMallSession', () => ({
  useMallSession: () => ({
    state: {
      session: mallSession,
      error: null,
      isSubmitting: false,
    },
    isAuthenticated: ref(true),
    bootstrap: vi.fn(),
    login: vi.fn(),
    signOut: vi.fn(),
  }),
}))

vi.mock('../../composables/useMallOrderStatus', () => ({
  useMallOrderStatus: () => mallOrderStatusMock.current,
}))

vi.mock('../../composables/useMallCart', () => ({
  useMallCart: () => ({
    sessionScope: ref({ shopId: 1, userId: '10001' }),
    restoreResult: ref({ status: 'empty' }),
    items: ref([]),
    itemCount: ref(0),
    totalPreviewCent: ref(0),
    errorMessage: ref(''),
    checkoutFailure: ref(null),
    isCheckingOut: ref(false),
    canCheckout: ref(false),
    orderRequestId: ref('req-cart'),
    addItem: vi.fn(),
    submitCheckout: vi.fn(async () => null),
    setQuantity: vi.fn(),
    clearCart: vi.fn(),
    removeItem: vi.fn(),
  }),
}))

const emptyProductPage: ProductListResponse = {
  items: [],
  total: 0,
  page: 1,
  size: 12,
}

const emptyReviewPage: ProductReviewPageResponse = {
  productId: 301,
  averageRating: 0,
  reviewCount: 0,
  ratingDistribution: {},
  page: 1,
  size: 5,
  items: [],
}

vi.mock('../../services/productApi', () => ({
  listProducts: vi.fn(async () => createApiResult(emptyProductPage)),
  getProduct: vi.fn(),
  listProductReviews: vi.fn(async () => createApiResult(emptyReviewPage)),
}))

vi.mock('../../services/uploadApi', () => ({
  uploadReviewImage: vi.fn(),
}))

interface MallOrderStatusMock {
  order: Ref<OrderResponse | null>
  orders: Ref<OrderResponse[]>
  payment: Ref<PaymentResponse | null>
  paymentNo: Ref<string>
  paymentFailure: Ref<null>
  total: Ref<number>
  page: Ref<number>
  size: Ref<number>
  errorMessage: Ref<string>
  isLoadingOrder: Ref<boolean>
  isLoadingOrders: Ref<boolean>
  isRefreshingPayment: Ref<boolean>
  isCancelling: Ref<boolean>
  isConfirmingReceipt: Ref<boolean>
  isSubmittingReview: Ref<boolean>
  isSubmittingPayment: Ref<boolean>
  orderRefreshResult: Ref<'idle' | 'success' | 'error'>
  canCancel: Ref<boolean>
  canPay: Ref<boolean>
  canConfirmReceipt: Ref<boolean>
  canReview: Ref<boolean>
  paymentStatus: Ref<string>
  loadOrder: ReturnType<typeof vi.fn>
  refreshCurrentOrder: ReturnType<typeof vi.fn>
  loadOrders: ReturnType<typeof vi.fn>
  refreshPayment: ReturnType<typeof vi.fn>
  cancelCurrentOrder: ReturnType<typeof vi.fn>
  confirmCurrentOrderReceipt: ReturnType<typeof vi.fn>
  submitCurrentOrderReview: ReturnType<typeof vi.fn>
  submitPayment: ReturnType<typeof vi.fn>
  acceptCreatedOrder: ReturnType<typeof vi.fn>
  acceptPayment: ReturnType<typeof vi.fn>
}

describe('MallStorefrontView order status rendering', () => {
  let orderStatus: MallOrderStatusMock
  let wrapper: VueWrapper | null

  beforeEach(() => {
    orderStatus = createOrderStatusMock()
    mallOrderStatusMock.current = orderStatus
    wrapper?.unmount()
    wrapper = null
  })

  it.each([
    {
      name: 'shipped',
      order: createOrder({
        status: 'shipped',
        fulfillmentStatus: 'shipped',
        carrier: 'SF Express',
        trackingNo: 'SF999',
        shippedAt: '2026-05-07T12:00:00+08:00',
      }),
      expectedStatus: 'mall.orders.statusShipped',
      expectedFulfillment: 'mall.orders.statusShipped',
      expectedRefreshTitle: 'mall.orders.paymentRefreshDisabledShipped',
      canRefreshPayment: false,
    },
    {
      name: 'completed',
      order: createOrder({
        status: 'completed',
        fulfillmentStatus: 'completed',
        completedAt: '2026-05-07T13:00:00+08:00',
        reviewed: true,
        review: createReview(),
      }),
      expectedStatus: 'mall.orders.statusCompleted',
      expectedFulfillment: 'mall.orders.statusCompleted',
      expectedRefreshTitle: 'mall.orders.paymentRefreshDisabledCompleted',
      canRefreshPayment: false,
    },
    {
      name: 'cancelled',
      order: createOrder({ status: 'cancelled' }),
      expectedStatus: 'mall.orders.statusCancelled',
      expectedFulfillment: 'mall.orders.statusCancelled',
      expectedRefreshTitle: 'mall.orders.paymentRefreshDisabledCancelled',
      canRefreshPayment: false,
    },
    {
      name: 'unknown',
      order: createOrder({ status: 'refunding', fulfillmentStatus: null }),
      expectedStatus: 'refunding',
      expectedFulfillment: 'mall.orders.logisticsUnknownMessage',
      expectedRefreshTitle: 'mall.orders.paymentRefreshAvailablePAY-refresh',
      canRefreshPayment: true,
    },
  ])('renders $name orders without awaiting-shipment regression', async ({
    order,
    expectedStatus,
    expectedFulfillment,
    expectedRefreshTitle,
    canRefreshPayment,
  }) => {
    setCurrentOrder(order)
    wrapper = mountMallStorefront()

    const detailText = wrapper.find('.order-detail').text()
    const refreshPaymentButton = findButtonByText(wrapper, 'mall.orders.refreshPayment')

    expect(detailText).toContain(expectedStatus)
    expect(detailText).toContain(expectedFulfillment)
    expect(detailText).not.toContain('mall.orders.statusPaidAwaitingShipment')
    expect(detailText).not.toContain('mall.orders.awaitingShipment')
    expect(refreshPaymentButton.attributes('title')).toBe(expectedRefreshTitle)
    if (canRefreshPayment) {
      expect(refreshPaymentButton.attributes('disabled')).toBeUndefined()
    } else {
      expect(refreshPaymentButton.attributes()).toHaveProperty('disabled', '')
    }
  })

  it('renders backend payment refresh errors while keeping the shipped snapshot visible', () => {
    setCurrentOrder(createOrder({
      status: 'shipped',
      fulfillmentStatus: 'shipped',
      carrier: 'SF Express',
      trackingNo: 'SF999',
      shippedAt: '2026-05-07T12:00:00+08:00',
    }))
    orderStatus.errorMessage.value = 'PAYMENT_REFRESH_FAILED: Payment service unavailable. Trace ID trace-payment-shipped.'

    wrapper = mountMallStorefront()

    const detailText = wrapper.find('.order-detail').text()
    expect(detailText).toContain('mall.orders.refreshFailedKeepDetail')
    expect(detailText).toContain('PAYMENT_REFRESH_FAILED: Payment service unavailable. Trace ID trace-payment-shipped.')
    expect(detailText).toContain('mall.orders.statusShipped')
    expect(detailText).toContain('SF Express')
    expect(detailText).toContain('SF999')
  })

  it('renders available and loading payment refresh states for paid awaiting-shipment orders', async () => {
    setCurrentOrder(createOrder({
      status: 'paid',
      fulfillmentStatus: 'unshipped',
    }))
    wrapper = mountMallStorefront()

    let refreshPaymentButton = findButtonByText(wrapper, 'mall.orders.refreshPayment')
    expect(refreshPaymentButton.attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.payment-source').text()).toBe('mall.orders.paymentRefreshAvailablePAY-refresh')

    orderStatus.isRefreshingPayment.value = true
    await wrapper.vm.$nextTick()

    refreshPaymentButton = findButtonByText(wrapper, 'common.refreshing')
    expect(refreshPaymentButton.attributes()).toHaveProperty('disabled', '')
  })

  function setCurrentOrder(order: OrderResponse) {
    orderStatus.order.value = order
    orderStatus.orders.value = [order]
    orderStatus.paymentNo.value = 'PAY-refresh'
    orderStatus.total.value = 1
  }
})

function mountMallStorefront(): VueWrapper {
  return mount(MallStorefrontView, {
    global: {
      stubs: {
        ProductCheckoutPanel: true,
      },
    },
  })
}

function findButtonByText(wrapper: VueWrapper, text: string) {
  const button = wrapper
    .findAll('button')
    .find((candidate) => candidate.text() === text)

  if (!button) {
    throw new Error(`Button with text "${text}" was not rendered.`)
  }

  return button
}

function createOrderStatusMock(): MallOrderStatusMock {
  const order = ref<OrderResponse | null>(null)
  const payment = ref<PaymentResponse | null>(null)
  const isCancelling = ref(false)
  const isConfirmingReceipt = ref(false)
  const isSubmittingPayment = ref(false)

  return {
    order,
    orders: ref([]),
    payment,
    paymentNo: ref(''),
    paymentFailure: ref(null),
    total: ref(0),
    page: ref(1),
    size: ref(5),
    errorMessage: ref(''),
    isLoadingOrder: ref(false),
    isLoadingOrders: ref(false),
    isRefreshingPayment: ref(false),
    isCancelling,
    isConfirmingReceipt,
    isSubmittingReview: ref(false),
    isSubmittingPayment,
    orderRefreshResult: ref('idle'),
    canCancel: computed(() => order.value?.status === 'created' && !isCancelling.value),
    canPay: computed(() => order.value?.status === 'created' && !payment.value && !isSubmittingPayment.value),
    canConfirmReceipt: computed(() => order.value?.status === 'shipped' && !isConfirmingReceipt.value),
    canReview: computed(() => order.value?.status === 'completed' && !order.value.reviewed),
    paymentStatus: computed(() => payment.value?.status ?? order.value?.status ?? 'unknown'),
    loadOrder: vi.fn(async () => null),
    refreshCurrentOrder: vi.fn(async () => null),
    loadOrders: vi.fn(async () => null),
    refreshPayment: vi.fn(async () => null),
    cancelCurrentOrder: vi.fn(async () => null),
    confirmCurrentOrderReceipt: vi.fn(async () => null),
    submitCurrentOrderReview: vi.fn(async () => null),
    submitPayment: vi.fn(async () => null),
    acceptCreatedOrder: vi.fn(),
    acceptPayment: vi.fn(),
  }
}

function createApiResult<T>(data: T) {
  return {
    code: 'SUCCESS',
    message: 'OK',
    data,
    traceId: 'trace-test',
    timestamp: '2026-05-16T00:00:00+08:00',
  }
}

function createOrder(patch: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderId: 501,
    orderNo: 'ORD-501',
    shopId: 1,
    userId: '10001',
    requestId: 'req-501',
    status: 'paid',
    totalAmountCent: 59900,
    items: [
      {
        productId: 301,
        skuId: 401,
        skuName: 'Daily trainer 42',
        priceCent: 59900,
        quantity: 1,
        lineAmountCent: 59900,
      },
    ],
    createdAt: '2026-05-07T10:00:00+08:00',
    updatedAt: '2026-05-07T12:00:00+08:00',
    fulfillmentStatus: 'unshipped',
    carrier: null,
    trackingNo: null,
    shippedAt: null,
    completedAt: null,
    reviewed: false,
    review: null,
    ...patch,
  }
}

function createReview() {
  return {
    orderReviewId: 9001,
    shopId: 1,
    orderId: 501,
    orderNo: 'ORD-501',
    userId: '10001',
    rating: 5,
    content: 'good',
    imageUrls: [],
    requestId: 'review-001',
    traceId: 'trace-review',
    createdAt: '2026-05-08T10:00:00+08:00',
  }
}
