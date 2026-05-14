// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import OrderManagementView from './OrderManagementView.vue'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { AdminOrderSummaryResponse, AdminOrderDetailResponse } from '../../types/api/order'
import type { PaymentResponse } from '../../types/api/payment'
import type { ApiResponseMeta } from '../../types/api/common'

vi.mock('../../services/orderApi', () => ({
  listAdminOrders: vi.fn(),
  getAdminOrder: vi.fn(),
  cancelAdminOrder: vi.fn(),
}))

import { listAdminOrders, getAdminOrder, cancelAdminOrder } from '../../services/orderApi'
import { getAdminPaymentByOrderId } from '../../services/paymentApi'
import { HttpClientError } from '../../services/httpClient'

vi.mock('../../services/paymentApi', () => ({
  getAdminPaymentByOrderId: vi.fn(),
}))

vi.mock('../../composables/useAppPreferences', () => ({
  useAppPreferences: () => ({
    t: (key: string) => key,
    locale: { value: 'zh-Hans' },
    theme: { value: 'light' },
  }),
}))

const mockSession: PersistedOpsSession = {
  userId: 1,
  shopId: 1,
  username: 'admin',
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresAt: '2099-12-31T23:59:59+08:00',
  roles: ['ADMIN'],
  permissions: ['ORDER_MANAGEMENT_ADMIN'],
}

const mockMeta: ApiResponseMeta = {
  code: 'SUCCESS',
  message: 'OK',
  traceId: 'test-trace',
  timestamp: '2026-05-09T00:00:00+08:00',
  status: 200,
}

function createOrderSummary(patch: Partial<AdminOrderSummaryResponse> = {}): AdminOrderSummaryResponse {
  return {
    orderId: 1,
    orderNo: 'ORD-001',
    shopId: 1,
    userId: '10001',
    status: 'created',
    totalAmountCent: 59900,
    paymentNo: null,
    itemCount: 2,
    traceId: null,
    createdAt: '2026-05-08T10:00:00+08:00',
    updatedAt: '2026-05-08T10:00:00+08:00',
    ...patch,
  }
}

function createOrderDetail(patch: Partial<AdminOrderDetailResponse> = {}): AdminOrderDetailResponse {
  return {
    orderId: 1,
    orderNo: 'ORD-001',
    shopId: 1,
    userId: '10001',
    requestId: 'req-001',
    reservationNo: 'RSV-001',
    paymentNo: null,
    status: 'created',
    totalAmountCent: 59900,
    traceId: 'trace-detail',
    createdAt: '2026-05-08T10:00:00+08:00',
    updatedAt: '2026-05-08T10:00:00+08:00',
    items: [
      { productId: 301, skuId: 401, skuName: 'Sneaker 42', priceCent: 59900, quantity: 1, lineAmountCent: 59900 },
      { productId: 302, skuId: 402, skuName: 'Sneaker 43', priceCent: 0, quantity: 1, lineAmountCent: 0 },
    ],
    statusTimeline: [
      { status: 'created', occurredAt: '2026-05-08T10:00:00+08:00', traceId: 'trace-created' },
    ],
    ...patch,
  }
}

function createPayment(patch: Partial<PaymentResponse> = {}): PaymentResponse {
  return {
    paymentId: 201,
    paymentNo: 'PAY-201',
    orderId: 1,
    orderNo: 'ORD-001',
    shopId: 1,
    userId: '10001',
    channel: 'mock',
    status: 'paid',
    amountCent: 59900,
    ...patch,
  }
}

function flushPromises(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function createControlledApiResponse() {
  let resolveResponse: (value: unknown) => void = () => {
    throw new Error('controlled promise resolver not initialized')
  }
  const promise = new Promise<unknown>((resolve) => {
    resolveResponse = resolve
  })
  return { promise, resolve: resolveResponse }
}

let wrapper: VueWrapper | null = null

async function mountView(overrides: {
  session?: PersistedOpsSession | null
  canAccessOrderWorkspace?: boolean
  initialOrderId?: number | null
} = {}) {
  const w = mount(OrderManagementView, {
    props: {
      session: overrides.session !== undefined ? overrides.session : mockSession,
      canAccessOrderWorkspace: overrides.canAccessOrderWorkspace ?? true,
      initialOrderId: overrides.initialOrderId ?? null,
    },
  })
  wrapper = w
  await flushPromises()
  await nextTick()
  return w
}

async function selectOrderFromList(w: VueWrapper) {
  await w.find('.list-item').trigger('click')
  await flushPromises()
  await nextTick()
}

function findRefreshPaymentButton(w: VueWrapper) {
  const button = w.findAll('.detail-actions .secondary')[1]
  if (!button) {
    throw new Error('refresh payment button not found')
  }
  return button
}

function resetBrowserState() {
  window.sessionStorage.clear()
  window.history.replaceState(null, '', '/admin')
}

// --- Permission / Gate ---

describe('OrderManagementView no-access prop gate', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: { items: [createOrderSummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('does not call listAdminOrders when canAccessOrderWorkspace is false', async () => {
    await mountView({ canAccessOrderWorkspace: false })
    expect(vi.mocked(listAdminOrders)).not.toHaveBeenCalled()
  })

  it('does not call listAdminOrders when session is missing', async () => {
    await mountView({ session: null })
    expect(vi.mocked(listAdminOrders)).not.toHaveBeenCalled()
  })

  it('calls listAdminOrders when canAccessOrderWorkspace is true and session exists', async () => {
    await mountView()
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalled()
  })
})

// --- List Failure / Retry / Empty ---

describe('OrderManagementView list failure and retry', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('renders error banner with backend code/message/traceId on list failure', async () => {
    vi.mocked(listAdminOrders).mockRejectedValue(
      new HttpClientError('Order list failed', { code: 'ORDER_STATUS_INVALID', status: 409, traceId: 'trace-order-list-001' }),
    )

    const w = await mountView()

    const errorBanner = w.find('.banner.error')
    expect(errorBanner.exists()).toBe(true)
    expect(errorBanner.text()).toContain('Order list failed')
    expect(errorBanner.text()).toContain('ORDER_STATUS_INVALID')
    expect(errorBanner.text()).toContain('trace-order-list-001')
  })

  it('does not render empty banner when list error is present', async () => {
    vi.mocked(listAdminOrders).mockRejectedValue(
      new HttpClientError('Order list failed', { code: 'ORDER_STATUS_INVALID', status: 409, traceId: 'trace-order-list-001' }),
    )

    const w = await mountView()

    expect(w.find('section.banner.empty').exists()).toBe(false)
  })

  it('retry calls listAdminOrders again and clears error on success', async () => {
    vi.mocked(listAdminOrders)
      .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-1' }))
      .mockResolvedValueOnce({
        data: { items: [createOrderSummary({ orderId: 1, orderNo: 'ORD-001' })], total: 1, page: 1, size: 20 },
        meta: mockMeta,
      })

    const w = await mountView()
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(1)

    const retryBtn = w.find('.banner.error button')
    await retryBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(2)
    expect(w.find('.banner.error').exists()).toBe(false)
    expect(w.findAll('.list-item').length).toBe(1)
  })
})

describe('OrderManagementView empty list success', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('renders empty banner when items is empty and no error', async () => {
    const w = await mountView()
    expect(w.find('.banner.empty').exists()).toBe(true)
    expect(w.find('.banner.error').exists()).toBe(false)
  })
})

// --- Completed Status Display ---

describe('OrderManagementView completed status display', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: {
        items: [createOrderSummary({ orderId: 1, orderNo: 'ORD-001', status: 'completed', paymentNo: null })],
        total: 1,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })
    vi.mocked(getAdminOrder).mockResolvedValue({
      data: createOrderDetail({
        orderId: 1,
        orderNo: 'ORD-001',
        status: 'completed',
        statusTimeline: [
          { status: 'created', occurredAt: '2026-05-08T10:00:00+08:00', traceId: 'trace-created' },
          { status: 'paid', occurredAt: '2026-05-08T11:00:00+08:00', traceId: 'trace-paid' },
          { status: 'shipped', occurredAt: '2026-05-08T12:00:00+08:00', traceId: 'trace-shipped' },
          { status: 'completed', occurredAt: '2026-05-09T10:00:00+08:00', traceId: 'trace-completed' },
        ],
      }),
      meta: mockMeta,
    })
    vi.mocked(getAdminPaymentByOrderId).mockRejectedValue(
      new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
    )
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('displays completed label in list item', async () => {
    const w = await mountView()
    expect(w.text()).toContain('orderAdmin.statusCompleted')
  })

  it('displays completed label and timeline description in detail', async () => {
    const w = await mountView()
    await selectOrderFromList(w)

    expect(w.text()).toContain('orderAdmin.statusCompleted')
    expect(w.text()).toContain('orderAdmin.timelineCompletedDescription')
  })

  it('completed order cannot be cancelled', async () => {
    const w = await mountView()
    await selectOrderFromList(w)

    const cancelBtn = w.find('.detail-actions .danger')
    expect(cancelBtn.attributes('disabled')).toBe('')
  })

  it('includes completed in status filter dropdown', async () => {
    const w = await mountView()
    const statusSelect = w.find('.filters select')
    const options = statusSelect.findAll('option')
    const optionValues = options.map((opt) => opt.attributes('value'))
    expect(optionValues).toContain('completed')
  })
})

// --- Filter Query / Reset ---

describe('OrderManagementView search and reset filters', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: { items: [createOrderSummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminPaymentByOrderId).mockRejectedValue(
      new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
    )
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('omits status=all and blank filters from search query', async () => {
    const w = await mountView()
    vi.mocked(listAdminOrders).mockClear()

    const filterSection = w.find('.filters')
    const selects = filterSection.findAll('select')
    const inputs = filterSection.findAll('input')

    await selects[0].setValue('all')
    await inputs[0].setValue('')
    await inputs[1].setValue('')

    const searchBtn = filterSection.find('.filter-actions .primary')
    await searchBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
    })
  })

  it('normalizes datetime-local values and includes non-blank filters', async () => {
    const w = await mountView()
    vi.mocked(listAdminOrders).mockClear()

    const filterSection = w.find('.filters')
    const selects = filterSection.findAll('select')
    const inputs = filterSection.findAll('input')

    await selects[0].setValue('paid')
    await inputs[0].setValue('ORD-002')
    await inputs[1].setValue('10002')
    await inputs[2].setValue('2026-05-07T10:00')
    await inputs[3].setValue('2026-05-08T18:00')

    const searchBtn = filterSection.find('.filter-actions .primary')
    await searchBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'paid',
      orderNo: 'ORD-002',
      userId: '10002',
      fromTime: '2026-05-07T10:00:00+08:00',
      toTime: '2026-05-08T18:00:00+08:00',
    })
  })

  it('reset calls listAdminOrders with default filters', async () => {
    const w = await mountView()
    vi.mocked(listAdminOrders).mockClear()

    const filterSection = w.find('.filters')
    const inputs = filterSection.findAll('input')

    await inputs[0].setValue('ORD-002')

    const resetBtn = filterSection.find('.filter-actions .secondary')
    await resetBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
    })
  })
})

// --- Deep Link ---

describe('OrderManagementView deep link with initialOrderId', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: {
        items: [
          createOrderSummary({ orderId: 101, orderNo: 'ORD-101', status: 'paid', paymentNo: null }),
          createOrderSummary({ orderId: 102, orderNo: 'ORD-102', status: 'created' }),
        ],
        total: 2,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('loads detail via getAdminOrder when initialOrderId is provided', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue({
      data: createOrderDetail({ orderId: 101, orderNo: 'ORD-101', status: 'paid' }),
      meta: mockMeta,
    })
    vi.mocked(getAdminPaymentByOrderId).mockRejectedValue(
      new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
    )

    const w = await mountView({ initialOrderId: 101 })

    expect(vi.mocked(getAdminOrder)).toHaveBeenCalledWith(101)
    expect(w.text()).toContain('ORD-101')
    expect(w.text()).toContain('RSV-001')
  })
})

// --- Cancel Confirmation / Failure / Retry / Duplicate Guard ---

describe('OrderManagementView cancel order', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: {
        items: [createOrderSummary({ orderId: 1, orderNo: 'ORD-001', status: 'created' })],
        total: 1,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })
    vi.mocked(getAdminOrder).mockResolvedValue({
      data: createOrderDetail({ orderId: 1, orderNo: 'ORD-001', status: 'created' }),
      meta: mockMeta,
    })
    vi.mocked(getAdminPaymentByOrderId).mockRejectedValue(
      new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
    )
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
    resetBrowserState()
  })

  it('opens confirmation dialog on cancel click and does not call API before confirm', async () => {
    const w = await mountView()
    await selectOrderFromList(w)

    const cancelBtn = w.find('.detail-actions .danger')
    await cancelBtn.trigger('click')
    await nextTick()

    expect(w.find('.confirm-backdrop').exists()).toBe(true)
    expect(vi.mocked(cancelAdminOrder)).not.toHaveBeenCalled()
  })

  it('cancel failure restores button state and displays backend error details', async () => {
    vi.mocked(cancelAdminOrder).mockRejectedValue(
      new HttpClientError('Only created orders can be cancelled', {
        code: 'ORDER_STATUS_INVALID',
        status: 409,
        traceId: 'trace-order-cancel-001',
      }),
    )

    const w = await mountView()
    await selectOrderFromList(w)

    const cancelBtn = w.find('.detail-actions .danger')
    await cancelBtn.trigger('click')
    await nextTick()

    const confirmBtn = w.find('.confirm-dialog .danger')
    await confirmBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(cancelAdminOrder)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(cancelAdminOrder)).toHaveBeenCalledWith(
      1,
      { requestId: '00000000-0000-0000-0000-000000000001' },
    )
    expect(confirmBtn.attributes('disabled')).toBeUndefined()

    const actionBanner = w.find('.banner.error')
    expect(actionBanner.text()).toContain('Only created orders can be cancelled')
    expect(actionBanner.text()).toContain('ORDER_STATUS_INVALID')
    expect(actionBanner.text()).toContain('trace-order-cancel-001')
  })

  it('retry after cancel failure sends another request', async () => {
    vi.mocked(cancelAdminOrder)
      .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-cancel-1' }))
      .mockResolvedValueOnce({ data: createOrderDetail({ orderId: 1, status: 'cancelled' }), meta: mockMeta })

    const w = await mountView()
    await selectOrderFromList(w)

    const cancelBtn = w.find('.detail-actions .danger')
    await cancelBtn.trigger('click')
    await nextTick()

    const confirmBtn = w.find('.confirm-dialog .danger')
    await confirmBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(cancelAdminOrder)).toHaveBeenCalledTimes(1)

    await confirmBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(cancelAdminOrder)).toHaveBeenCalledTimes(2)
  })

  it('duplicate confirm click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(cancelAdminOrder).mockReturnValue(controlled.promise as ReturnType<typeof cancelAdminOrder>)

    const w = await mountView()
    await selectOrderFromList(w)

    const cancelBtn = w.find('.detail-actions .danger')
    await cancelBtn.trigger('click')
    await nextTick()

    const confirmBtn = w.find('.confirm-dialog .danger')
    await confirmBtn.trigger('click')
    await nextTick()

    await confirmBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(cancelAdminOrder)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createOrderDetail({ orderId: 1, status: 'cancelled' }), meta: mockMeta })
    await flushPromises()
  })
})

// --- Payment Refresh ---

describe('OrderManagementView payment refresh', () => {
  beforeEach(() => {
    vi.mocked(listAdminOrders).mockResolvedValue({
      data: {
        items: [createOrderSummary({ orderId: 1, orderNo: 'ORD-001', status: 'created', paymentNo: null })],
        total: 1,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })
    vi.mocked(getAdminOrder).mockResolvedValue({
      data: createOrderDetail({ orderId: 1, orderNo: 'ORD-001', status: 'created', paymentNo: null }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    resetBrowserState()
  })

  it('PAYMENT_NOT_FOUND during automatic detail load does not render payment error', async () => {
    vi.mocked(getAdminPaymentByOrderId).mockRejectedValue(
      new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
    )

    const w = await mountView()
    await selectOrderFromList(w)

    const paymentBanner = w.findAll('.banner.error').filter((b) => b.text().includes('trace-pay'))
    expect(paymentBanner.length).toBe(0)
    expect(w.text()).toContain('ORD-001')
  })

  it('manual refresh failure preserves current snapshot and displays backend error', async () => {
    vi.mocked(getAdminPaymentByOrderId)
      .mockRejectedValueOnce(
        new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
      )
      .mockRejectedValueOnce(
        new HttpClientError('Service unavailable', { code: 'DOWNSTREAM_TIMEOUT', status: 503, traceId: 'trace-down' }),
      )

    const w = await mountView()
    await selectOrderFromList(w)

    const refreshPaymentBtn = findRefreshPaymentButton(w)
    await refreshPaymentBtn.trigger('click')
    await flushPromises()
    await nextTick()

    const paymentBanner = w.find('.banner.error')
    expect(paymentBanner.text()).toContain('Service unavailable')
    expect(paymentBanner.text()).toContain('DOWNSTREAM_TIMEOUT')
    expect(paymentBanner.text()).toContain('trace-down')

    expect(w.text()).toContain('ORD-001')
    expect(w.text()).toContain('RSV-001')
  })

  it('manual refresh success merges payment status into detail and list item', async () => {
    vi.mocked(getAdminPaymentByOrderId)
      .mockRejectedValueOnce(
        new HttpClientError('Payment not found', { code: 'PAYMENT_NOT_FOUND', status: 404, traceId: 'trace-pay' }),
      )
      .mockResolvedValueOnce({ data: createPayment({ paymentNo: 'PAY-201', status: 'paid' }), meta: mockMeta })

    const w = await mountView()
    await selectOrderFromList(w)

    const refreshPaymentBtn = findRefreshPaymentButton(w)
    await refreshPaymentBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.text()).toContain('PAY-201')
  })
})
