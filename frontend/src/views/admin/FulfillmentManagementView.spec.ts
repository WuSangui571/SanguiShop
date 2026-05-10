// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import FulfillmentManagementView from './FulfillmentManagementView.vue'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { AdminFulfillmentResponse } from '../../types/api/order'
import type { ApiResponseMeta } from '../../types/api/common'

vi.mock('../../services/fulfillmentApi', () => ({
  listAdminFulfillments: vi.fn(),
  getAdminFulfillment: vi.fn(),
  shipAdminFulfillment: vi.fn(),
}))

import { listAdminFulfillments, getAdminFulfillment, shipAdminFulfillment } from '../../services/fulfillmentApi'
import { HttpClientError } from '../../services/httpClient'

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
  permissions: ['LOGISTICS_FULFILLMENT_ADMIN'],
}

const mockMeta: ApiResponseMeta = {
  code: 'SUCCESS',
  message: 'OK',
  traceId: 'test-trace',
  timestamp: '2026-05-09T00:00:00+08:00',
  status: 200,
}

function createFulfillment(patch: Partial<AdminFulfillmentResponse> = {}): AdminFulfillmentResponse {
  return {
    orderId: 1,
    orderNo: 'ORD-001',
    shopId: 1,
    userId: '10001',
    status: 'paid',
    fulfillmentStatus: 'unshipped',
    totalAmountCent: 59900,
    carrier: null,
    trackingNo: null,
    shippedAt: null,
    traceId: null,
    createdAt: '2026-05-08T10:00:00+08:00',
    updatedAt: '2026-05-08T10:00:00+08:00',
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

async function mountView(overrides: { canAccessFulfillmentWorkspace?: boolean; session?: PersistedOpsSession } = {}) {
  const w = mount(FulfillmentManagementView, {
    props: {
      session: overrides.session ?? mockSession,
      canAccessFulfillmentWorkspace: overrides.canAccessFulfillmentWorkspace ?? true,
    },
  })
  wrapper = w
  await flushPromises()
  await nextTick()
  return w
}

describe('FulfillmentManagementView no-access prop gating', () => {
  beforeEach(() => {
    vi.mocked(listAdminFulfillments).mockResolvedValue({
      data: { items: [createFulfillment()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('does not call listAdminFulfillments when canAccessFulfillmentWorkspace is false', async () => {
    await mountView({ canAccessFulfillmentWorkspace: false })
    expect(vi.mocked(listAdminFulfillments)).not.toHaveBeenCalled()
  })

  it('calls listAdminFulfillments when canAccessFulfillmentWorkspace is true', async () => {
    vi.mocked(getAdminFulfillment).mockResolvedValue({ data: createFulfillment(), meta: mockMeta })
    await mountView()
    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalled()
  })
})

describe('FulfillmentManagementView list failure and retry', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('renders error banner with backend code/message/traceId on list failure', async () => {
    vi.mocked(listAdminFulfillments).mockRejectedValue(
      new HttpClientError('Fulfillment list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-fulfillment-001' }),
    )

    const w = await mountView()

    const errorBanner = w.find('.banner.error')
    expect(errorBanner.exists()).toBe(true)
    expect(errorBanner.text()).toContain('Fulfillment list failed')
    expect(errorBanner.text()).toContain('AUTH_FORBIDDEN')
    expect(errorBanner.text()).toContain('trace-fulfillment-001')
  })

  it('does not render empty banner when list error is present', async () => {
    vi.mocked(listAdminFulfillments).mockRejectedValue(
      new HttpClientError('Fulfillment list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-fulfillment-001' }),
    )

    const w = await mountView()

    expect(w.find('section.banner.empty').exists()).toBe(false)
  })

  it('retry calls listAdminFulfillments again and renders second result', async () => {
    vi.mocked(listAdminFulfillments)
      .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-1' }))
      .mockResolvedValueOnce({
        data: { items: [createFulfillment({ orderId: 1, orderNo: 'ORD-001' })], total: 1, page: 1, size: 20 },
        meta: mockMeta,
      })

    vi.mocked(getAdminFulfillment).mockResolvedValue({ data: createFulfillment(), meta: mockMeta })

    const w = await mountView()
    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledTimes(1)

    const retryBtn = w.find('.banner.error button')
    await retryBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledTimes(2)
    expect(w.find('.banner.error').exists()).toBe(false)
    expect(w.findAll('.list-item').length).toBe(1)
  })
})

describe('FulfillmentManagementView empty list success', () => {
  beforeEach(() => {
    vi.mocked(listAdminFulfillments).mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('renders empty banner when items is empty and no error', async () => {
    const w = await mountView()
    expect(w.find('.banner.empty').exists()).toBe(true)
    expect(w.find('.banner.error').exists()).toBe(false)
  })
})

describe('FulfillmentManagementView search and reset filters', () => {
  beforeEach(() => {
    vi.mocked(listAdminFulfillments).mockResolvedValue({
      data: { items: [createFulfillment()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminFulfillment).mockResolvedValue({ data: createFulfillment(), meta: mockMeta })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('search calls listAdminFulfillments with normalized query params', async () => {
    const w = await mountView()
    vi.mocked(listAdminFulfillments).mockClear()
    vi.mocked(getAdminFulfillment).mockClear()

    const filterSection = w.find('.filters')
    const selects = filterSection.findAll('select')
    const inputs = filterSection.findAll('input')

    await selects[0].setValue('all')
    await inputs[0].setValue('')
    await inputs[1].setValue('')
    await inputs[2].setValue('2026-05-07T10:00')
    await inputs[3].setValue('2026-05-08T18:00')

    const searchBtn = filterSection.find('.filter-actions .primary')
    await searchBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      fromTime: '2026-05-07T10:00:00+08:00',
      toTime: '2026-05-08T18:00:00+08:00',
    })
  })

  it('search with non-blank filters includes them in query', async () => {
    const w = await mountView()
    vi.mocked(listAdminFulfillments).mockClear()
    vi.mocked(getAdminFulfillment).mockClear()

    const filterSection = w.find('.filters')
    const selects = filterSection.findAll('select')
    const inputs = filterSection.findAll('input')

    await selects[0].setValue('unshipped')
    await inputs[0].setValue('ORD-002')
    await inputs[1].setValue('10002')
    await inputs[2].setValue('2026-05-07T10:00')
    await inputs[3].setValue('2026-05-08T18:00')

    const searchBtn = filterSection.find('.filter-actions .primary')
    await searchBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'unshipped',
      orderNo: 'ORD-002',
      userId: '10002',
      fromTime: '2026-05-07T10:00:00+08:00',
      toTime: '2026-05-08T18:00:00+08:00',
    })
  })

  it('reset calls listAdminFulfillments with default filters', async () => {
    const w = await mountView()
    vi.mocked(listAdminFulfillments).mockClear()
    vi.mocked(getAdminFulfillment).mockClear()

    const filterSection = w.find('.filters')
    const inputs = filterSection.findAll('input')

    await inputs[0].setValue('ORD-002')

    const resetBtn = filterSection.find('.filter-actions .secondary')
    await resetBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminFulfillments)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
    })
  })
})

describe('FulfillmentManagementView ship action failure recovery', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminFulfillments).mockResolvedValue({
      data: {
        items: [createFulfillment({ orderId: 1, orderNo: 'ORD-001', fulfillmentStatus: 'unshipped' })],
        total: 1,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })
    vi.mocked(getAdminFulfillment).mockResolvedValue({
      data: createFulfillment({ orderId: 1, orderNo: 'ORD-001', fulfillmentStatus: 'unshipped' }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('ship failure restores button enabled state and preserves draft inputs', async () => {
    vi.mocked(shipAdminFulfillment).mockRejectedValue(
      new HttpClientError('Ship failed', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-ship-err' }),
    )

    const w = await mountView()

    const shipForm = w.find('.ship-form')
    const submitBtn = shipForm.find('.primary')
    const inputs = shipForm.findAll('input')

    await inputs[0].setValue('SF Express')
    await inputs[1].setValue('SF123456')
    await shipForm.trigger('submit')
    await flushPromises()
    await nextTick()

    expect(submitBtn.attributes('disabled')).toBeUndefined()

    expect((inputs[0].element as HTMLInputElement).value).toBe('SF Express')
    expect((inputs[1].element as HTMLInputElement).value).toBe('SF123456')

    const actionBanner = w.find('.banner.error')
    expect(actionBanner.text()).toContain('Ship failed')
    expect(actionBanner.text()).toContain('VALIDATION_FAILED')
    expect(actionBanner.text()).toContain('trace-ship-err')
  })

  it('ship retry after failure sends another request', async () => {
    vi.mocked(shipAdminFulfillment)
      .mockRejectedValueOnce(new HttpClientError('Ship failed', { code: 'ERROR', status: 500, traceId: 'trace-ship-retry' }))
      .mockResolvedValueOnce({ data: createFulfillment({ orderId: 1, fulfillmentStatus: 'shipped' }), meta: mockMeta })

    const w = await mountView()

    const shipForm = w.find('.ship-form')
    const inputs = shipForm.findAll('input')

    await inputs[0].setValue('SF Express')
    await inputs[1].setValue('SF123456')

    await shipForm.trigger('submit')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(shipAdminFulfillment)).toHaveBeenCalledTimes(1)

    await shipForm.trigger('submit')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(shipAdminFulfillment)).toHaveBeenCalledTimes(2)
    expect(vi.mocked(shipAdminFulfillment)).toHaveBeenCalledWith(
      1,
      { requestId: '00000000-0000-0000-0000-000000000001', carrier: 'SF Express', trackingNo: 'SF123456' },
    )
  })

  it('duplicate ship click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(shipAdminFulfillment).mockReturnValue(controlled.promise as ReturnType<typeof shipAdminFulfillment>)

    const w = await mountView()

    const shipForm = w.find('.ship-form')
    const inputs = shipForm.findAll('input')

    await inputs[0].setValue('SF Express')
    await inputs[1].setValue('SF123456')

    await shipForm.trigger('submit')
    await nextTick()

    await shipForm.trigger('submit')
    await nextTick()

    expect(vi.mocked(shipAdminFulfillment)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createFulfillment({ orderId: 1, fulfillmentStatus: 'shipped' }), meta: mockMeta })
    await flushPromises()
  })
})
