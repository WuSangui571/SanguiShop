// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import SeckillActivityManagementView from './SeckillActivityManagementView.vue'
import type { PersistedOpsSession } from '../../types/api/auth'
import type {
  AdminSeckillActivitySummaryResponse,
  AdminSeckillActivityDetailResponse,
  AdminSeckillActivitySkuResponse,
} from '../../types/api/seckill'
import type { ApiResponseMeta } from '../../types/api/common'

vi.mock('../../services/seckillApi', () => ({
  listAdminSeckillActivities: vi.fn(),
  getAdminSeckillActivity: vi.fn(),
  createAdminSeckillActivity: vi.fn(),
  updateAdminSeckillActivity: vi.fn(),
  updateAdminSeckillActivityStatus: vi.fn(),
  bindAdminSeckillActivitySku: vi.fn(),
}))

import {
  listAdminSeckillActivities,
  getAdminSeckillActivity,
  createAdminSeckillActivity,
  updateAdminSeckillActivity,
  updateAdminSeckillActivityStatus,
  bindAdminSeckillActivitySku,
} from '../../services/seckillApi'
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
  permissions: ['SECKILL_ACTIVITY_ADMIN'],
}

const mockMeta: ApiResponseMeta = {
  code: 'SUCCESS',
  message: 'OK',
  traceId: 'test-trace',
  timestamp: '2026-05-09T00:00:00+08:00',
  status: 200,
}

function createActivitySummary(patch: Partial<AdminSeckillActivitySummaryResponse> = {}): AdminSeckillActivitySummaryResponse {
  return {
    activityId: 1,
    activityName: 'Test Activity',
    status: 'draft',
    startsAt: '2026-06-01T00:00:00+08:00',
    endsAt: '2026-06-07T23:59:59+08:00',
    serverTime: '2026-05-10T12:00:00+08:00',
    skuCount: 2,
    totalActivityStock: 100,
    soldCount: 0,
    ...patch,
  }
}

function createSkuResponse(patch: Partial<AdminSeckillActivitySkuResponse> = {}): AdminSeckillActivitySkuResponse {
  return {
    productId: 101,
    productName: 'Test Product',
    skuId: 1001,
    skuCode: 'SKU-001',
    skuName: 'Sku 1',
    priceCent: 59900,
    seckillPriceCent: 49900,
    availableStock: 50,
    activityStock: 30,
    soldCount: 0,
    ...patch,
  }
}

function createActivityDetail(patch: Partial<AdminSeckillActivityDetailResponse> = {}): AdminSeckillActivityDetailResponse {
  return {
    activityId: 1,
    activityName: 'Test Activity',
    status: 'draft',
    startsAt: '2026-06-01T00:00:00+08:00',
    endsAt: '2026-06-07T23:59:59+08:00',
    serverTime: '2026-05-10T12:00:00+08:00',
    skuCount: 1,
    totalActivityStock: 30,
    soldCount: 0,
    description: 'Activity description',
    skus: [createSkuResponse()],
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
  canAccessSeckillWorkspace?: boolean
  session?: PersistedOpsSession | null
} = {}) {
  const w = mount(SeckillActivityManagementView, {
    props: {
      session: overrides.session !== undefined ? overrides.session : mockSession,
      canAccessSeckillWorkspace: overrides.canAccessSeckillWorkspace ?? true,
    },
  })
  wrapper = w
  await flushPromises()
  await nextTick()
  return w
}

describe('SeckillActivityManagementView no-access prop gating', () => {
  beforeEach(() => {
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('does not call listAdminSeckillActivities when canAccessSeckillWorkspace is false', async () => {
    await mountView({ canAccessSeckillWorkspace: false })
    expect(vi.mocked(listAdminSeckillActivities)).not.toHaveBeenCalled()
  })

  it('calls listAdminSeckillActivities when canAccessSeckillWorkspace is true', async () => {
    await mountView()
    expect(vi.mocked(listAdminSeckillActivities)).toHaveBeenCalled()
  })

  it('does not call listAdminSeckillActivities when session is missing', async () => {
    await mountView({ session: null })
    expect(vi.mocked(listAdminSeckillActivities)).not.toHaveBeenCalled()
  })
})

describe('SeckillActivityManagementView list failure and retry', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('renders error banner with backend code/message/traceId on list failure', async () => {
    vi.mocked(listAdminSeckillActivities).mockRejectedValue(
      new HttpClientError('Activity list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-list-001' }),
    )

    const w = await mountView()

    const errorBanner = w.find('.banner.error')
    expect(errorBanner.exists()).toBe(true)
    expect(errorBanner.text()).toContain('Activity list failed')
    expect(errorBanner.text()).toContain('AUTH_FORBIDDEN')
    expect(errorBanner.text()).toContain('trace-list-001')
  })

  it('does not render empty banner when list error is present', async () => {
    vi.mocked(listAdminSeckillActivities).mockRejectedValue(
      new HttpClientError('Activity list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-list-001' }),
    )

    const w = await mountView()

    expect(w.find('.banner.empty').exists()).toBe(false)
  })

  it('retry calls listAdminSeckillActivities again and renders second result', async () => {
    vi.mocked(listAdminSeckillActivities)
      .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-1' }))
      .mockResolvedValueOnce({
        data: { items: [createActivitySummary({ activityId: 1, activityName: 'Retried Activity' })], total: 1, page: 1, size: 20 },
        meta: mockMeta,
      })

    const w = await mountView()
    expect(vi.mocked(listAdminSeckillActivities)).toHaveBeenCalledTimes(1)

    const retryBtn = w.find('.banner.error button')
    await retryBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminSeckillActivities)).toHaveBeenCalledTimes(2)
    expect(w.find('.banner.error').exists()).toBe(false)
    expect(w.findAll('.list-item').length).toBe(1)
  })
})

describe('SeckillActivityManagementView empty list success', () => {
  beforeEach(() => {
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
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

describe('SeckillActivityManagementView filter and default query', () => {
  beforeEach(() => {
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail(),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('initial list call uses page=1, size=20, status=all', async () => {
    await mountView()
    expect(vi.mocked(listAdminSeckillActivities)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'all',
    })
  })

  it('changing filter status calls list with selected status', async () => {
    const w = await mountView()
    vi.mocked(listAdminSeckillActivities).mockClear()
    vi.mocked(getAdminSeckillActivity).mockClear()

    const select = w.find('.toolbar select')
    await select.setValue('active')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminSeckillActivities)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'active',
    })
  })
})

describe('SeckillActivityManagementView status labels', () => {
  beforeEach(() => {
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail(),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('renders unknown status fallback when status is not recognized', async () => {
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail({ status: 'flash_freeze' }),
      meta: mockMeta,
    })

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.find('.meta').text()).toContain('flash_freeze')
  })

  it('renders time display based on response serverTime field', async () => {
    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.text()).toContain('2026-05-10T12:00:00+08:00')
  })

  it('lists activity items with draft, scheduled, active, ended statuses', async () => {
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: {
        items: [
          createActivitySummary({ activityId: 1, activityName: 'Draft Act', status: 'draft' }),
          createActivitySummary({ activityId: 2, activityName: 'Scheduled Act', status: 'scheduled' }),
          createActivitySummary({ activityId: 3, activityName: 'Active Act', status: 'active' }),
          createActivitySummary({ activityId: 4, activityName: 'Ended Act', status: 'ended' }),
        ],
        total: 4,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })

    const w = await mountView()
    expect(w.findAll('.list-item').length).toBe(4)
  })
})

describe('SeckillActivityManagementView save failure and duplicate guard', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary({ activityId: 1, activityName: 'Test Activity' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail({ activityId: 1, activityName: 'Test Activity' }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('create failure preserves draft and shows backend error details', async () => {
    vi.mocked(createAdminSeckillActivity).mockRejectedValue(
      new HttpClientError('Activity create failed', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-create-001' }),
    )

    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 20 },
      meta: mockMeta,
    })

    const w = await mountView()
    const inputs = w.find('.form-grid').findAll('input')
    const textarea = w.find('.form-grid textarea')

    await inputs[0].setValue('New Activity')
    await textarea.setValue('New Description')
    await inputs[1].setValue('2026-06-01T00:00:00+08:00')
    await inputs[2].setValue('2026-06-07T23:59:59+08:00')
    await nextTick()

    const saveBtn = w.find('.detail-actions .primary')
    expect(saveBtn.attributes('disabled')).toBeUndefined()

    await saveBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(createAdminSeckillActivity)).toHaveBeenCalledWith({
      shopId: 1,
      userId: '1',
      activityName: 'New Activity',
      description: 'New Description',
      startsAt: '2026-06-01T00:00:00+08:00',
      endsAt: '2026-06-07T23:59:59+08:00',
      skus: [],
    })
    expect((inputs[0].element as HTMLInputElement).value).toBe('New Activity')

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Activity create failed')
    expect(actionErrors[0].text()).toContain('VALIDATION_FAILED')
    expect(actionErrors[0].text()).toContain('trace-create-001')
    expect(w.find('.detail-actions .primary').attributes('disabled')).toBeUndefined()
  })

  it('update failure preserves detail and shows backend error details', async () => {
    vi.mocked(updateAdminSeckillActivity).mockRejectedValue(
      new HttpClientError('Activity save failed', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-save-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const saveBtn = w.find('.detail-actions .primary')
    expect(saveBtn.exists()).toBe(true)

    await saveBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.find('.detail-panel').text()).toContain('Test Activity')

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Activity save failed')
    expect(actionErrors[0].text()).toContain('VALIDATION_FAILED')
    expect(actionErrors[0].text()).toContain('trace-save-001')

    const saveButtonAfter = w.find('.detail-actions .primary')
    expect(saveButtonAfter.attributes('disabled')).toBeUndefined()
  })

  it('duplicate save click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(updateAdminSeckillActivity).mockReturnValue(controlled.promise as ReturnType<typeof updateAdminSeckillActivity>)

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const saveBtn = w.find('.detail-actions .primary')
    expect(saveBtn.exists()).toBe(true)

    await saveBtn.trigger('click')
    await nextTick()

    await saveBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(updateAdminSeckillActivity)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createActivityDetail(), meta: mockMeta })
    await flushPromises()
  })
})

describe('SeckillActivityManagementView status update failure and duplicate guard', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary({ activityId: 1, status: 'draft' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail({ activityId: 1, status: 'draft' }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('status update failure preserves current detail and shows backend error with requestId', async () => {
    vi.mocked(updateAdminSeckillActivityStatus).mockRejectedValue(
      new HttpClientError('Status update failed', { code: 'SECKILL_ACTIVITY_NOT_FOUND', status: 404, traceId: 'trace-status-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const detailActions = w.find('.detail-actions')
    expect(detailActions.exists()).toBe(true)

    const statusButtons = detailActions.findAll('.secondary')
    expect(statusButtons.length).toBeGreaterThanOrEqual(1)

    const publishBtn = statusButtons[0]
    await publishBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.find('.detail-panel').text()).toContain('Test Activity')

    expect(vi.mocked(updateAdminSeckillActivityStatus)).toHaveBeenCalledWith(
      1,
      { status: 'active', requestId: '00000000-0000-0000-0000-000000000001' },
    )

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Status update failed')
    expect(actionErrors[0].text()).toContain('SECKILL_ACTIVITY_NOT_FOUND')
    expect(actionErrors[0].text()).toContain('trace-status-001')
  })

  it('duplicate status click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(updateAdminSeckillActivityStatus).mockReturnValue(controlled.promise as ReturnType<typeof updateAdminSeckillActivityStatus>)

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const detailActions = w.find('.detail-actions')
    const statusButtons = detailActions.findAll('.secondary')
    const publishBtn = statusButtons[0]

    await publishBtn.trigger('click')
    await nextTick()

    await publishBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(updateAdminSeckillActivityStatus)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createActivityDetail({ status: 'active' }), meta: mockMeta })
    await flushPromises()
  })
})

describe('SeckillActivityManagementView SKU bind failure and duplicate guard', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary({ activityId: 1 })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail({
        activityId: 1,
        skus: [createSkuResponse({ skuId: 1001, skuCode: 'SKU-001', availableStock: 50 })],
      }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('SKU bind failure preserves current detail and shows backend error', async () => {
    vi.mocked(bindAdminSeckillActivitySku).mockRejectedValue(
      new HttpClientError('SKU bind failed', { code: 'STOCK_NOT_ENOUGH', status: 409, traceId: 'trace-sku-bind-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.text()).toContain('Sku 1')

    const skuActions = w.find('.sku-card .sku-actions')
    const bindBtn = skuActions.find('.secondary')
    expect(bindBtn.exists()).toBe(true)

    const stockInputs = skuActions.findAll('input')
    await stockInputs[0].setValue('25')

    await bindBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(bindAdminSeckillActivitySku)).toHaveBeenCalledWith(
      1,
      {
        productId: 101,
        skuId: 1001,
        activityStock: 25,
        seckillPriceCent: undefined,
        requestId: '00000000-0000-0000-0000-000000000001',
      },
    )

    expect(w.text()).toContain('Sku 1')

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('SKU bind failed')
    expect(actionErrors[0].text()).toContain('STOCK_NOT_ENOUGH')
    expect(actionErrors[0].text()).toContain('trace-sku-bind-001')
  })

  it('duplicate SKU bind click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(bindAdminSeckillActivitySku).mockReturnValue(controlled.promise as ReturnType<typeof bindAdminSeckillActivitySku>)

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const skuActions = w.find('.sku-card .sku-actions')
    const bindBtn = skuActions.find('.secondary')

    const stockInputs = skuActions.findAll('input')
    await stockInputs[0].setValue('25')

    await bindBtn.trigger('click')
    await nextTick()

    await bindBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(bindAdminSeckillActivitySku)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createActivityDetail(), meta: mockMeta })
    await flushPromises()
  })
})

describe('SeckillActivityManagementView stock validation', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary({ activityId: 1 })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail({
        activityId: 1,
        skus: [createSkuResponse({ skuId: 1001, availableStock: 50 })],
      }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('blocks negative activity stock locally', async () => {
    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const skuActions = w.find('.sku-card .sku-actions')
    const stockInputs = skuActions.findAll('input')
    await stockInputs[0].setValue('-1')

    const bindBtn = skuActions.find('.secondary')
    await bindBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(bindAdminSeckillActivitySku)).not.toHaveBeenCalled()
    expect(w.text()).toContain('seckillAdmin.activityStockNegative')
  })

  it('blocks activityStock > availableStock locally', async () => {
    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const skuActions = w.find('.sku-card .sku-actions')
    const stockInputs = skuActions.findAll('input')
    await stockInputs[0].setValue('100')

    const bindBtn = skuActions.find('.secondary')
    await bindBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(bindAdminSeckillActivitySku)).not.toHaveBeenCalled()
    expect(w.text()).toContain('seckillAdmin.activityStockExceeds')
  })
})

describe('SeckillActivityManagementView detail load failure', () => {
  beforeEach(() => {
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary({ activityId: 1, activityName: 'Test Activity' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('preserves current detail and shows backend error when detail load fails', async () => {
    vi.mocked(getAdminSeckillActivity).mockRejectedValue(
      new HttpClientError('Detail load failed', { code: 'SECKILL_ACTIVITY_NOT_FOUND', status: 404, traceId: 'trace-detail-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const detailErrorBanner = w.find('.detail-panel .banner.error')
    expect(detailErrorBanner.exists()).toBe(true)
    expect(detailErrorBanner.text()).toContain('Detail load failed')
    expect(detailErrorBanner.text()).toContain('SECKILL_ACTIVITY_NOT_FOUND')
    expect(detailErrorBanner.text()).toContain('trace-detail-001')
  })
})

describe('SeckillActivityManagementView requestId generation', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminSeckillActivities).mockResolvedValue({
      data: { items: [createActivitySummary({ activityId: 1, status: 'draft' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminSeckillActivity).mockResolvedValue({
      data: createActivityDetail({ activityId: 1, status: 'draft' }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('status update payload includes requestId', async () => {
    vi.mocked(updateAdminSeckillActivityStatus).mockRejectedValue(
      new HttpClientError('Status update failed', { code: 'ERROR', status: 500, traceId: 'trace-status' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const publishBtn = w.find('.detail-actions .secondary')
    await publishBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(updateAdminSeckillActivityStatus)).toHaveBeenCalledWith(
      1,
      { status: 'active', requestId: '00000000-0000-0000-0000-000000000001' },
    )
  })
})
