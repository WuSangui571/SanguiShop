// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ProductManagementView from './ProductManagementView.vue'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { ProductAdminSummaryResponse, ProductDetailResponse, ProductSkuResponse } from '../../types/api/product'
import type { ApiResponseMeta } from '../../types/api/common'

vi.mock('../../services/productApi', () => ({
  listAdminProducts: vi.fn(),
  getAdminProduct: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  updateProductStatus: vi.fn(),
  adjustSkuStock: vi.fn(),
}))

import { listAdminProducts, getAdminProduct, createProduct, updateProduct, updateProductStatus, adjustSkuStock } from '../../services/productApi'
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
  permissions: ['PRODUCT_CATALOG_ADMIN'],
}

const mockMeta: ApiResponseMeta = {
  code: 'SUCCESS',
  message: 'OK',
  traceId: 'test-trace',
  timestamp: '2026-05-09T00:00:00+08:00',
  status: 200,
}

function createProductSummary(patch: Partial<ProductAdminSummaryResponse> = {}): ProductAdminSummaryResponse {
  return {
    productId: 1,
    productName: 'Test Product',
    productDescription: 'Test Description',
    minPriceCent: 59900,
    maxPriceCent: 69900,
    status: 'active',
    skuCount: 2,
    availableStockTotal: 100,
    reservedStockTotal: 10,
    ...patch,
  }
}

function createSkuResponse(patch: Partial<ProductSkuResponse> = {}): ProductSkuResponse {
  return {
    skuId: 101,
    skuCode: 'SKU-001',
    skuName: 'Sku 1',
    priceCent: 59900,
    availableStock: 50,
    reservedStock: 5,
    ...patch,
  }
}

function createProductDetail(patch: Partial<ProductDetailResponse> = {}): ProductDetailResponse {
  return {
    productId: 1,
    productName: 'Test Product',
    productDescription: 'Test Description',
    status: 'active',
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

async function mountView(overrides: { canAccessProductWorkspace?: boolean; session?: PersistedOpsSession | null } = {}) {
  const w = mount(ProductManagementView, {
    props: {
      session: overrides.session !== undefined ? overrides.session : mockSession,
      canAccessProductWorkspace: overrides.canAccessProductWorkspace ?? true,
    },
  })
  wrapper = w
  await flushPromises()
  await nextTick()
  return w
}

describe('ProductManagementView no-access prop gating', () => {
  beforeEach(() => {
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('does not call listAdminProducts when canAccessProductWorkspace is false', async () => {
    await mountView({ canAccessProductWorkspace: false })
    expect(vi.mocked(listAdminProducts)).not.toHaveBeenCalled()
  })

  it('calls listAdminProducts when canAccessProductWorkspace is true', async () => {
    await mountView()
    expect(vi.mocked(listAdminProducts)).toHaveBeenCalled()
  })

  it('does not call listAdminProducts when session is missing', async () => {
    await mountView({ session: null })
    expect(vi.mocked(listAdminProducts)).not.toHaveBeenCalled()
  })
})

describe('ProductManagementView list failure and retry', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('renders error banner with backend code/message/traceId on list failure', async () => {
    vi.mocked(listAdminProducts).mockRejectedValue(
      new HttpClientError('Product list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-list-001' }),
    )

    const w = await mountView()

    const errorBanner = w.find('.banner.error')
    expect(errorBanner.exists()).toBe(true)
    expect(errorBanner.text()).toContain('Product list failed')
    expect(errorBanner.text()).toContain('AUTH_FORBIDDEN')
    expect(errorBanner.text()).toContain('trace-list-001')
  })

  it('does not render empty banner when list error is present', async () => {
    vi.mocked(listAdminProducts).mockRejectedValue(
      new HttpClientError('Product list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-list-001' }),
    )

    const w = await mountView()

    expect(w.find('.banner.empty').exists()).toBe(false)
  })

  it('retry calls listAdminProducts again and renders second result', async () => {
    vi.mocked(listAdminProducts)
      .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-1' }))
      .mockResolvedValueOnce({
        data: { items: [createProductSummary({ productId: 1, productName: 'Retried Product' })], total: 1, page: 1, size: 20 },
        meta: mockMeta,
      })

    const w = await mountView()
    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledTimes(1)

    const retryBtn = w.find('.banner.error button')
    await retryBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledTimes(2)
    expect(w.find('.banner.error').exists()).toBe(false)
    expect(w.findAll('.list-item').length).toBe(1)
  })
})

describe('ProductManagementView empty list success', () => {
  beforeEach(() => {
    vi.mocked(listAdminProducts).mockResolvedValue({
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

describe('ProductManagementView filter and default query', () => {
  beforeEach(() => {
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail(),
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
    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'all',
    })
  })

  it('changing filter status calls listAdminProducts with selected status', async () => {
    const w = await mountView()
    vi.mocked(listAdminProducts).mockClear()
    vi.mocked(getAdminProduct).mockClear()

    const select = w.find('.toolbar select')
    await select.setValue('active')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'active',
    })
  })

  it('returning filter to default status calls list with all', async () => {
    const w = await mountView()
    vi.mocked(listAdminProducts).mockClear()
    vi.mocked(getAdminProduct).mockClear()

    const select = w.find('.toolbar select')
    await select.setValue('active')
    await flushPromises()
    await nextTick()
    vi.mocked(listAdminProducts).mockClear()
    vi.mocked(getAdminProduct).mockClear()

    await select.setValue('all')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: 'all',
    })
  })
})

describe('ProductManagementView product detail and SKU loading', () => {
  beforeEach(() => {
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary({ productId: 1, productName: 'Test Product' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('selecting a list item loads detail via getAdminProduct and renders product name', async () => {
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail({ productId: 1, productName: 'Test Product', skus: [createSkuResponse({ skuCode: 'SKU-001' })] }),
      meta: mockMeta,
    })

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(getAdminProduct)).toHaveBeenCalledWith(1)

    const skuInputs = w.find('.sku-grid').findAll('input')
    expect((skuInputs[0].element as HTMLInputElement).value).toBe('SKU-001')
  })

  it('detail failure preserves backend error details', async () => {
    vi.mocked(getAdminProduct).mockRejectedValue(
      new HttpClientError('Detail load failed', { code: 'PRODUCT_NOT_FOUND', status: 404, traceId: 'trace-detail-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const detailErrorBanner = w.find('.detail-panel .banner.error')
    expect(detailErrorBanner.exists()).toBe(true)
    expect(detailErrorBanner.text()).toContain('Detail load failed')
    expect(detailErrorBanner.text()).toContain('PRODUCT_NOT_FOUND')
    expect(detailErrorBanner.text()).toContain('trace-detail-001')
  })

  it('renders unknown status fallback when status is not recognized', async () => {
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail({ productId: 1, productName: 'Unknown Status Item', status: 'viral_hit' }),
      meta: mockMeta,
    })

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.find('.meta').text()).toContain('viral_hit')
  })
})

describe('ProductManagementView save failure and duplicate guard', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary({ productId: 1, productName: 'Test Product' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail({ productId: 1, productName: 'Test Product' }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('update failure preserves draft and shows backend error details', async () => {
    vi.mocked(updateProduct).mockRejectedValue(
      new HttpClientError('Product save failed', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-save-001' }),
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

    expect(w.find('.detail-panel').text()).toContain('Test Product')

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Product save failed')
    expect(actionErrors[0].text()).toContain('VALIDATION_FAILED')
    expect(actionErrors[0].text()).toContain('trace-save-001')

    const saveButtonAfter = w.find('.detail-actions .primary')
    expect(saveButtonAfter.attributes('disabled')).toBeUndefined()
  })

  it('create failure preserves draft and shows backend error details', async () => {
    vi.mocked(createProduct).mockRejectedValue(
      new HttpClientError('Product create failed', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-create-001' }),
    )

    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 20 },
      meta: mockMeta,
    })

    const w = await mountView()
    const inputs = w.find('.form-grid').findAll('input')
    const textarea = w.find('.form-grid textarea')
    const skuInputs = w.find('.sku-grid').findAll('input')

    await inputs[0].setValue('New Product')
    await textarea.setValue('New Description')
    await skuInputs[0].setValue('SKU-NEW')
    await skuInputs[1].setValue('New SKU')
    await skuInputs[2].setValue('1299')
    await skuInputs[3].setValue('7')
    await nextTick()

    const saveBtn = w.find('.detail-actions .primary')
    expect(saveBtn.attributes('disabled')).toBeUndefined()

    await saveBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(createProduct)).toHaveBeenCalledWith({
      shopId: 1,
      userId: '1',
      productName: 'New Product',
      productDescription: 'New Description',
      skus: [{
        skuCode: 'SKU-NEW',
        skuName: 'New SKU',
        priceCent: 1299,
        availableStock: 7,
      }],
    })
    expect((inputs[0].element as HTMLInputElement).value).toBe('New Product')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('New Description')

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Product create failed')
    expect(actionErrors[0].text()).toContain('VALIDATION_FAILED')
    expect(actionErrors[0].text()).toContain('trace-create-001')
    expect(w.find('.detail-actions .primary').attributes('disabled')).toBeUndefined()
  })

  it('duplicate save click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(updateProduct).mockReturnValue(controlled.promise as ReturnType<typeof updateProduct>)

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

    expect(vi.mocked(updateProduct)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createProductDetail(), meta: mockMeta })
    await flushPromises()
  })
})

describe('ProductManagementView status update failure and duplicate guard', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary({ productId: 1, status: 'active' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail({ productId: 1, status: 'active' }),
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
    vi.mocked(updateProductStatus).mockRejectedValue(
      new HttpClientError('Status update failed', { code: 'PRODUCT_STATUS_INVALID', status: 409, traceId: 'trace-status-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const detailActions = w.find('.detail-actions')
    expect(detailActions.exists()).toBe(true)

    const statusButtons = detailActions.findAll('.secondary')
    const deactivateBtn = statusButtons[1]
    await deactivateBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(w.find('.detail-panel').text()).toContain('Test Product')

    expect(vi.mocked(updateProductStatus)).toHaveBeenCalledWith(
      1,
      { status: 'inactive', requestId: '00000000-0000-0000-0000-000000000001' },
    )

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Status update failed')
    expect(actionErrors[0].text()).toContain('PRODUCT_STATUS_INVALID')
    expect(actionErrors[0].text()).toContain('trace-status-001')
  })

  it('duplicate status click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(updateProductStatus).mockReturnValue(controlled.promise as ReturnType<typeof updateProductStatus>)

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const detailActions = w.find('.detail-actions')
    const statusButtons = detailActions.findAll('.secondary')
    const deactivateBtn = statusButtons[1]

    await deactivateBtn.trigger('click')
    await nextTick()

    await deactivateBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(updateProductStatus)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createProductDetail({ status: 'inactive' }), meta: mockMeta })
    await flushPromises()
  })
})

describe('ProductManagementView stock adjustment failure and duplicate guard', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary({ productId: 1 })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail({ productId: 1, skus: [createSkuResponse({ skuId: 101, skuCode: 'SKU-001', availableStock: 50 })] }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('stock adjustment failure preserves SKU snapshot and shows backend error with requestId', async () => {
    vi.mocked(adjustSkuStock).mockRejectedValue(
      new HttpClientError('Stock adjustment failed', { code: 'STOCK_INSUFFICIENT', status: 409, traceId: 'trace-stock-001' }),
    )

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const skuCard = w.find('.sku-card')
    expect(skuCard.exists()).toBe(true)

    const adjustBtn = skuCard.find('.sku-actions .secondary')
    expect(adjustBtn.exists()).toBe(true)
    await adjustBtn.trigger('click')
    await flushPromises()
    await nextTick()

    const skuInputs = w.find('.sku-grid').findAll('input')
    expect((skuInputs[0].element as HTMLInputElement).value).toBe('SKU-001')

    expect(vi.mocked(adjustSkuStock)).toHaveBeenCalledWith(
      1,
      101,
      { availableStock: 0, requestId: '00000000-0000-0000-0000-000000000001' },
    )

    const actionErrors = w.find('.detail-panel').findAll('.banner.error')
    expect(actionErrors.length).toBe(1)
    expect(actionErrors[0].text()).toContain('Stock adjustment failed')
    expect(actionErrors[0].text()).toContain('STOCK_INSUFFICIENT')
    expect(actionErrors[0].text()).toContain('trace-stock-001')
  })

  it('duplicate stock click while pending does not send second request', async () => {
    const controlled = createControlledApiResponse()
    vi.mocked(adjustSkuStock).mockReturnValue(controlled.promise as ReturnType<typeof adjustSkuStock>)

    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const skuCard = w.find('.sku-card')
    expect(skuCard.exists()).toBe(true)

    const adjustBtn = skuCard.find('.sku-actions .secondary')
    expect(adjustBtn.exists()).toBe(true)

    await adjustBtn.trigger('click')
    await nextTick()

    await adjustBtn.trigger('click')
    await nextTick()

    expect(vi.mocked(adjustSkuStock)).toHaveBeenCalledTimes(1)

    controlled.resolve({ data: createProductDetail(), meta: mockMeta })
    await flushPromises()
  })
})

describe('ProductManagementView validation boundaries', () => {
  beforeEach(() => {
    vi.mocked(listAdminProducts).mockResolvedValue({
      data: { items: [createProductSummary({ productId: 1, productName: 'Test Product' })], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
    vi.mocked(getAdminProduct).mockResolvedValue({
      data: createProductDetail({ productId: 1, productName: 'Test Product', skus: [createSkuResponse({ skuId: 101, skuCode: 'SKU-001' })] }),
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('duplicate SKU code renders duplicate validation error', async () => {
    const w = await mountView()

    await w.find('.list-item').trigger('click')
    await flushPromises()
    await nextTick()

    const addSkuBtn = w.find('.panel-head.compact .secondary')
    await addSkuBtn.trigger('click')
    await nextTick()

    const skuCards = w.findAll('.sku-card')
    const secondSkuGrid = skuCards[1].find('.sku-grid')
    const secondSkuInputs = secondSkuGrid.findAll('input')
    const secondSkuCodeInput = secondSkuInputs[0]
    await secondSkuCodeInput.setValue('SKU-001')
    await nextTick()

    const skuSection = w.find('.sku-section')
    const allSmalls = skuSection.findAll('small')
    const duplicateErrors = allSmalls.filter((el) => el.text() === 'productAdmin.duplicateSku')

    expect(duplicateErrors.length).toBeGreaterThanOrEqual(1)
  })
})
