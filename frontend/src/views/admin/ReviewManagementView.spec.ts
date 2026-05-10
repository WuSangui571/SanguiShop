// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ReviewManagementView from './ReviewManagementView.vue'
import type { PersistedOpsSession } from '../../types/api/auth'
import type { AdminReviewSummaryResponse } from '../../types/api/order'
import type { ApiResponseMeta } from '../../types/api/common'

// --- Mocks ---

vi.mock('../../services/orderApi', () => ({
  listAdminReviews: vi.fn(),
  updateAdminReviewVisibility: vi.fn(),
  upsertAdminReviewReply: vi.fn(),
  updateAdminReviewReplyVisibility: vi.fn(),
}))

import { listAdminReviews, updateAdminReviewVisibility, upsertAdminReviewReply, updateAdminReviewReplyVisibility } from '../../services/orderApi'
import { HttpClientError } from '../../services/httpClient'

vi.mock('../../composables/useAppPreferences', () => ({
  useAppPreferences: () => ({
    t: (key: string, params?: Record<string, string | number>): string => {
      const texts: Record<string, string> = {
        'admin.workspaceLabel': 'Admin workspace',
        'admin.productWorkspace': 'Products',
        'admin.orderWorkspace': 'Orders',
        'admin.reviewWorkspace': 'Reviews',
        'admin.compensationWorkspace': 'Ops',
        'reviewAdmin.closePreview': '关闭预览',
        'reviewAdmin.previewLabel': '评价 {id} 图片预览',
        'reviewAdmin.imageAlt': '评价 {id} 图片',
        'reviewAdmin.imageLoadFailed': '图片加载失败',
        'reviewAdmin.imageUrl': '图片 URL',
        'reviewAdmin.imageUnknown': '{count} 张图片待后端返回 URL',
        'reviewAdmin.imageCount': '{count} 张图片',
        'reviewAdmin.reviewId': '评价 {id}',
        'reviewAdmin.contentEmpty': '未填写评价内容',
        'reviewAdmin.ratingValue': '{rating} 星',
        'reviewAdmin.productId': '商品 ID',
        'reviewAdmin.sku': 'SKU',
        'reviewAdmin.userId': '用户',
        'reviewAdmin.orderNo': '订单号',
        'reviewAdmin.operator': '操作人',
        'reviewAdmin.updatedAt': '治理时间',
        'reviewAdmin.visibility': '可见状态',
        'reviewAdmin.visibilityAll': '全部状态',
        'reviewAdmin.visibilityVisible': '公开显示',
        'reviewAdmin.visibilityHidden': '已隐藏',
        'reviewAdmin.ratingAll': '全部评分',
        'reviewAdmin.ratingFive': '5 星',
        'reviewAdmin.ratingFour': '4 星',
        'reviewAdmin.ratingThree': '3 星',
        'reviewAdmin.ratingTwo': '2 星',
        'reviewAdmin.ratingOne': '1 星',
        'reviewAdmin.reason': '原因',
        'reviewAdmin.reasonPlaceholder': '隐藏或恢复原因',
        'reviewAdmin.search': '查询评价',
        'reviewAdmin.totalReviews': '共 {count} 条评价',
        'reviewAdmin.fromTime': '开始时间',
        'reviewAdmin.toTime': '结束时间',
        'reviewAdmin.listEmpty': '当前筛选条件下没有评价',
        'reviewAdmin.refreshList': '刷新列表',
        'reviewAdmin.kicker': '评价管理',
        'reviewAdmin.title': '评价查询与展示治理',
        'reviewAdmin.intro': '查看评价',
        'reviewAdmin.hide': '隐藏',
        'reviewAdmin.restore': '恢复显示',
        'reviewAdmin.updating': '更新中...',
        'reviewAdmin.replyTitle': '商家回复',
        'reviewAdmin.reply': '回复',
        'reviewAdmin.editReply': '编辑回复',
        'reviewAdmin.replyPlaceholder': '请输入 1-300 字回复',
        'reviewAdmin.replyVisible': '回复公开',
        'reviewAdmin.replyHidden': '回复已隐藏',
        'reviewAdmin.replyNone': '暂无回复',
        'reviewAdmin.hideReply': '隐藏回复',
        'reviewAdmin.restoreReply': '恢复回复',
        'common.shopId': '店铺 ID',
        'common.usernameOrMobile': '用户名或手机号',
        'common.code': 'code',
        'common.traceId': 'Trace ID',
        'common.loading': '加载中...',
        'common.prev': '上一页',
        'common.next': '下一页',
        'common.retry': '重试',
        'common.dismiss': '关闭',
        'dashboard.reset': '重置',
        'orderAdmin.pageLabel': '第 {page} / {total} 页',
      }
      let template = texts[key] ?? key
      if (params) {
        template = template.replace(/\{(\w+)\}/g, (_, k) => {
          const val = params[k]
          return val === undefined ? `{${k}}` : String(val)
        })
      }
      return template
    },
    locale: { value: 'zh-Hans' },
    theme: { value: 'light' },
  }),
}))

// --- Helpers ---

const mockSession: PersistedOpsSession = {
  userId: 1,
  shopId: 1,
  username: 'admin',
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresAt: '2099-12-31T23:59:59+08:00',
  roles: ['admin'],
  permissions: ['review:manage'],
}

const mockMeta: ApiResponseMeta = {
  code: 'SUCCESS',
  message: 'OK',
  traceId: 'test-trace',
  timestamp: '2026-05-09T00:00:00+08:00',
  status: 200,
}

function createReview(patch: Partial<AdminReviewSummaryResponse> = {}): AdminReviewSummaryResponse {
  return {
    reviewId: 1,
    orderId: 101,
    orderNo: 'ORD-101',
    productId: 301,
    skuId: 401,
    skuName: 'Sneaker 42',
    rating: 5,
    content: 'Review content',
    imageCount: 0,
    imageUrls: [],
    maskedUserId: '10***01',
    visibilityStatus: 'visible',
    visibilityReason: null,
    visibilityRequestId: null,
    visibilityOperator: null,
    visibilityTraceId: null,
    visibilityUpdatedAt: null,
    replyContent: null,
    replyVisibilityStatus: 'visible',
    replyRequestId: null,
    replyOperator: null,
    replyTraceId: null,
    replyUpdatedAt: null,
    createdAt: '2026-05-08T10:00:00+08:00',
    ...patch,
  }
}

function flushPromises(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function dialogExists(): boolean {
  return queryDialog() !== null
}

function dialogPreviewSrc(): string | null {
  const img = queryDialog()?.querySelector('img')
  return img?.getAttribute('src') ?? null
}

function queryDialog(): HTMLElement | null {
  return document.body.querySelector('[role="dialog"]')
}

function requireElement(selector: string): HTMLElement {
  const element = document.body.querySelector(selector)
  if (!(element instanceof HTMLElement)) {
    throw new Error(`Expected to find element: ${selector}`)
  }
  return element
}

let wrapper: VueWrapper | null = null

async function mountView(overrides: { canAccessReviewWorkspace?: boolean; session?: PersistedOpsSession } = {}) {
  const w = mount(ReviewManagementView, {
    props: {
      session: overrides.session ?? mockSession,
      canAccessReviewWorkspace: overrides.canAccessReviewWorkspace ?? true,
    },
  })
  wrapper = w
  // Wait for the async bootstrap chain to settle and Vue to re-render
  await flushPromises()
  await nextTick()
  return w
}

// --- Tests ---

describe('ReviewManagementView image preview interaction', () => {
  beforeEach(() => {
    vi.mocked(listAdminReviews).mockResolvedValue({
      data: {
        items: [
          createReview({
            reviewId: 1,
            imageCount: 1,
            imageUrls: ['/images/review-1.jpg'],
            content: 'Previewable review',
          }),
          createReview({
            reviewId: 2,
            imageCount: 1,
            imageUrls: ['/images/review-2.jpg'],
            content: 'Fail-to-load image review',
          }),
          createReview({
            reviewId: 3,
            imageCount: 2,
            imageUrls: undefined,
            content: 'Unknown count review',
          }),
        ],
        total: 3,
        page: 1,
        size: 20,
      },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    // Remove leftover teleported overlay content from body.
    queryDialog()?.remove()
    vi.clearAllMocks()
  })

  it('opens preview overlay when clicking a previewable thumbnail', async () => {
    const w = await mountView()

    const thumbs = w.findAll('.previewable-thumb')
    expect(thumbs.length).toBeGreaterThanOrEqual(1)

    await thumbs[0].trigger('click')
    await nextTick()

    // Verify the teleported overlay appears in the DOM
    expect(dialogExists()).toBe(true)
    expect(dialogPreviewSrc()).toBe('/images/review-1.jpg')
  })

  it('closes preview overlay when clicking the close button', async () => {
    const w = await mountView()

    // Open preview
    const thumbs = w.findAll('.previewable-thumb')
    await thumbs[0].trigger('click')
    await nextTick()
    expect(dialogExists()).toBe(true)

    // Click the close button inside the teleported overlay
    const closeButton = requireElement('button[aria-label="关闭预览"]')
    closeButton.click()
    await nextTick()

    // Overlay should be gone
    expect(dialogExists()).toBe(false)
  })

  it('closes preview overlay when clicking the overlay backdrop', async () => {
    const w = await mountView()

    // Open preview
    const thumbs = w.findAll('.previewable-thumb')
    await thumbs[0].trigger('click')
    await nextTick()
    expect(dialogExists()).toBe(true)

    // Click directly on the overlay backdrop
    const overlay = requireElement('[role="dialog"]')
    overlay.click()
    await nextTick()

    // Overlay should be gone
    expect(dialogExists()).toBe(false)
  })

  it('closes preview overlay when pressing Escape', async () => {
    const w = await mountView()

    // Open preview
    const thumbs = w.findAll('.previewable-thumb')
    await thumbs[0].trigger('click')
    await nextTick()
    expect(dialogExists()).toBe(true)

    // Dispatch Escape keydown on window (component listens via addEventListener)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()

    // Overlay should be gone
    expect(dialogExists()).toBe(false)
  })

  it('does not open preview overlay after thumbnail load error', async () => {
    const w = await mountView()

    // Trigger error on the second review's thumbnail
    const thumbs = w.findAll('.previewable-thumb')
    expect(thumbs.length).toBeGreaterThanOrEqual(2)

    await thumbs[1].trigger('error')
    await nextTick()

    // After error, the fallback div should render
    const fallback = w.find('.review-image-fallback')
    expect(fallback.exists()).toBe(true)

    // The failed frame should have the "failed" class
    const failedFrames = w.findAll('.review-image-frame.failed')
    expect(failedFrames.length).toBeGreaterThanOrEqual(1)

    // Click on the fallback area should NOT open preview
    await fallback.trigger('click')
    await nextTick()
    expect(dialogExists()).toBe(false)

    // Verify the first thumbnail (review 1) still opens preview
    const remainingThumbs = w.findAll('.previewable-thumb')
    expect(remainingThumbs.length).toBeGreaterThanOrEqual(1)
    await remainingThumbs[0].trigger('click')
    await nextTick()
    expect(dialogExists()).toBe(true)
    expect(dialogPreviewSrc()).toBe('/images/review-1.jpg')
  })

  it('does not open preview overlay for unknown fallback', async () => {
    const w = await mountView()

    // Find the unknown fallback frame
    const unknownFrames = w.findAll('.review-image-frame.unknown')
    expect(unknownFrames.length).toBeGreaterThanOrEqual(1)

    // Click on the unknown fallback frame should NOT open preview
    await unknownFrames[0].trigger('click')
    await nextTick()
    expect(dialogExists()).toBe(false)

    // Verify the fallback text is rendered
    expect(unknownFrames[0].text()).toContain('2')
  })
})

describe('ReviewManagementView no-access prop gating', () => {
  beforeEach(() => {
    vi.mocked(listAdminReviews).mockResolvedValue({
      data: { items: [createReview()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('does not call listAdminReviews when canAccessReviewWorkspace is false', async () => {
    await mountView({ canAccessReviewWorkspace: false })
    expect(vi.mocked(listAdminReviews)).not.toHaveBeenCalled()
  })

  it('calls listAdminReviews when canAccessReviewWorkspace is true', async () => {
    await mountView()
    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledTimes(1)
  })
})

describe('ReviewManagementView list failure and retry', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('renders error banner with backend code/message/traceId on list failure', async () => {
    vi.mocked(listAdminReviews).mockRejectedValue(
      new HttpClientError('Review list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-list-err' }),
    )

    const w = await mountView()

    const errorBanner = w.find('.banner.error')
    expect(errorBanner.exists()).toBe(true)
    expect(errorBanner.text()).toContain('Review list failed')
    expect(errorBanner.text()).toContain('AUTH_FORBIDDEN')
    expect(errorBanner.text()).toContain('trace-list-err')
  })

  it('does not render empty banner when list error is present', async () => {
    vi.mocked(listAdminReviews).mockRejectedValue(
      new HttpClientError('Review list failed', { code: 'AUTH_FORBIDDEN', status: 403, traceId: 'trace-list-err' }),
    )

    const w = await mountView()

    expect(w.find('.banner.empty').exists()).toBe(false)
  })

  it('retry calls listAdminReviews again and renders second result', async () => {
    vi.mocked(listAdminReviews)
      .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-1' }))
      .mockResolvedValueOnce({
        data: { items: [createReview({ reviewId: 1, content: 'Retry success' })], total: 1, page: 1, size: 20 },
        meta: mockMeta,
      })

    const w = await mountView()
    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledTimes(1)

    const retryBtn = w.find('.banner.error button')
    await retryBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledTimes(2)
    expect(w.find('.banner.error').exists()).toBe(false)
    expect(w.findAll('.review-row').length).toBe(1)
  })
})

describe('ReviewManagementView empty list success', () => {
  beforeEach(() => {
    vi.mocked(listAdminReviews).mockResolvedValue({
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

describe('ReviewManagementView search and reset filters', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(listAdminReviews).mockResolvedValue({
      data: { items: [createReview()], total: 1, page: 1, size: 20 },
      meta: mockMeta,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.clearAllMocks()
  })

  it('search calls listAdminReviews with normalized query params', async () => {
    const w = await mountView()
    vi.mocked(listAdminReviews).mockClear()

    const filterSection = w.find('.filters')
    const inputs = filterSection.findAll('input')
    const selects = filterSection.findAll('select')

    await inputs[0].setValue('301')
    await selects[0].setValue('5')
    await inputs[1].setValue('10001')
    await selects[1].setValue('visible')
    await inputs[2].setValue('2026-05-08T10:00')
    await inputs[3].setValue('2026-05-09T10:00')

    const searchBtn = filterSection.find('.filter-actions .primary')
    await searchBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      productId: 301,
      rating: 5,
      userId: '10001',
      visibility: 'visible',
      fromTime: '2026-05-08T10:00:00+08:00',
      toTime: '2026-05-09T10:00:00+08:00',
    })
  })

  it('reset calls listAdminReviews with default filters', async () => {
    const w = await mountView()
    vi.mocked(listAdminReviews).mockClear()

    const filterSection = w.find('.filters')
    const inputs = filterSection.findAll('input')

    await inputs[0].setValue('301')

    const resetBtn = filterSection.find('.filter-actions .secondary')
    await resetBtn.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminReviews)).toHaveBeenCalledWith({
      page: 1,
      size: 20,
    })
  })
})

describe('ReviewManagementView governance actions', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('00000000-0000-0000-0000-000000000001')
    vi.mocked(listAdminReviews).mockResolvedValue({
      data: {
        items: [
          createReview({
            reviewId: 1,
            visibilityStatus: 'visible',
            replyContent: null,
            content: 'Visible no-reply review',
          }),
          createReview({
            reviewId: 2,
            visibilityStatus: 'hidden',
            replyContent: null,
            visibilityReason: 'inappropriate',
            content: 'Hidden no-reply review',
          }),
          createReview({
            reviewId: 3,
            visibilityStatus: 'visible',
            replyContent: 'Thank you!',
            replyVisibilityStatus: 'visible',
            content: 'Review with visible reply',
          }),
          createReview({
            reviewId: 4,
            visibilityStatus: 'visible',
            replyContent: 'Hidden reply content',
            replyVisibilityStatus: 'hidden',
            content: 'Review with hidden reply',
          }),
        ],
        total: 4,
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
    vi.restoreAllMocks()
  })

  function mountedWrapper(): VueWrapper {
    if (!wrapper) {
      throw new Error('Expected ReviewManagementView to be mounted')
    }
    return wrapper
  }

  function rowActions(index: number) {
    return mountedWrapper().findAll('.review-row')[index].find('.row-actions').findAll('button')
  }

  function replyActions(index: number) {
    return mountedWrapper().findAll('.review-row')[index].find('.reply-actions').findAll('button')
  }

  function createControlledApiResponse() {
    let resolveResponse: (value: unknown) => void = () => {
      throw new Error('Expected controlled promise resolver to be initialized')
    }
    const promise = new Promise<unknown>((resolve) => {
      resolveResponse = resolve
    })
    return { promise, resolve: resolveResponse }
  }

  describe('review visibility', () => {
    it('visible review enables hide and disables restore', async () => {
      await mountView()
      const btns = rowActions(0)
      expect(btns[0].attributes('disabled')).toBeUndefined()
      expect(btns[1].attributes('disabled')).toBeDefined()
    })

    it('hidden review enables restore and disables hide', async () => {
      await mountView()
      const btns = rowActions(1)
      expect(btns[0].attributes('disabled')).toBeDefined()
      expect(btns[1].attributes('disabled')).toBeUndefined()
    })

    it('hide click calls updateAdminReviewVisibility with trimmed reason and requestId', async () => {
      await mountView()
      // Set moderation reason with surrounding spaces
      const reasonInput = mountedWrapper().find('input[placeholder="隐藏或恢复原因"]')
      await reasonInput.setValue('  inappropriate  ')

      // Click hide button on visible review (row 0, button 0 = hide)
      const btns = rowActions(0)
      await btns[0].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewVisibility)).toHaveBeenCalledWith(
        1,
        { visibility: 'hidden', reason: 'inappropriate', requestId: '00000000-0000-0000-0000-000000000001' },
      )
    })

    it('restore click calls updateAdminReviewVisibility with target visibility and requestId', async () => {
      await mountView()

      // Click restore button on hidden review (row 1, button 1 = restore)
      const btns = rowActions(1)
      await btns[1].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewVisibility)).toHaveBeenCalledWith(
        2,
        { visibility: 'visible', reason: null, requestId: '00000000-0000-0000-0000-000000000001' },
      )
    })

    it('duplicate hide click while pending does not call API twice', async () => {
      const controlled = createControlledApiResponse()
      vi.mocked(updateAdminReviewVisibility).mockReturnValue(controlled.promise as ReturnType<typeof updateAdminReviewVisibility>)

      await mountView()
      const btns = rowActions(0)

      // First click
      await btns[0].trigger('click')
      await nextTick()

      // Second click while pending
      await btns[0].trigger('click')
      await nextTick()

      expect(vi.mocked(updateAdminReviewVisibility)).toHaveBeenCalledTimes(1)

      // Resolve so test can complete cleanly
      controlled.resolve({ data: createReview({ reviewId: 1, visibilityStatus: 'hidden' }), meta: mockMeta })
      await flushPromises()
    })

    it('visibility failure restores button availability and allows retry', async () => {
      vi.mocked(updateAdminReviewVisibility)
        .mockRejectedValueOnce(new HttpClientError('Hide failed', { code: 'ERROR', status: 500, traceId: 'trace-vis-err' }))
        .mockResolvedValueOnce({ data: createReview({ reviewId: 1, visibilityStatus: 'hidden' }), meta: mockMeta })

      await mountView()
      const btns = rowActions(0)

      // Click hide - fails
      await btns[0].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewVisibility)).toHaveBeenCalledTimes(1)

      // Buttons return to state-based availability
      expect(btns[0].attributes('disabled')).toBeUndefined()
      expect(btns[1].attributes('disabled')).toBeDefined()

      // Action error banner shows backend details
      const actionBanner = mountedWrapper().find('.banner.error')
      expect(actionBanner.text()).toContain('Hide failed')
      expect(actionBanner.text()).toContain('ERROR')

      // Retry click - succeeds
      await btns[0].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewVisibility)).toHaveBeenCalledTimes(2)
    })
  })

  describe('reply submit', () => {
    it('empty and whitespace-only reply draft disable submit button', async () => {
      await mountView()
      const rlAct = replyActions(0) // review 1 has no reply
      expect(rlAct[0].attributes('disabled')).toBeDefined()

      const row = mountedWrapper().findAll('.review-row')[0]
      const textarea = row.find('textarea')
      await textarea.setValue('   ')
      expect(replyActions(0)[0].attributes('disabled')).toBeDefined()
    })

    it('entered reply content calls upsertAdminReviewReply with trimmed content and requestId', async () => {
      await mountView()
      const row = mountedWrapper().findAll('.review-row')[0]
      const textarea = row.find('textarea')
      await textarea.setValue('  Great product!  ')

      const rlAct = replyActions(0)
      expect(rlAct[0].attributes('disabled')).toBeUndefined()

      await rlAct[0].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(upsertAdminReviewReply)).toHaveBeenCalledWith(
        1,
        { content: 'Great product!', requestId: '00000000-0000-0000-0000-000000000001' },
      )
    })

    it('duplicate reply submit while pending does not call API twice', async () => {
      const controlled = createControlledApiResponse()
      vi.mocked(upsertAdminReviewReply).mockReturnValue(controlled.promise as ReturnType<typeof upsertAdminReviewReply>)

      await mountView()
      const row = mountedWrapper().findAll('.review-row')[0]
      const textarea = row.find('textarea')
      await textarea.setValue('Pending reply')
      const rlAct = replyActions(0)

      await rlAct[0].trigger('click')
      await nextTick()
      await rlAct[0].trigger('click')
      await nextTick()

      expect(vi.mocked(upsertAdminReviewReply)).toHaveBeenCalledTimes(1)

      controlled.resolve({ data: createReview({ reviewId: 1, replyContent: 'Pending reply' }), meta: mockMeta })
      await flushPromises()
    })

    it('reply save failure displays backend error and preserves textarea content', async () => {
      vi.mocked(upsertAdminReviewReply).mockRejectedValue(
        new HttpClientError('Reply too long', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-reply-err' }),
      )

      await mountView()
      const row = mountedWrapper().findAll('.review-row')[0]
      const textarea = row.find('textarea')
      await textarea.setValue('Draft content to preserve')

      const rlAct = replyActions(0)
      await rlAct[0].trigger('click')
      await flushPromises()
      await nextTick()

      // Error banner displays backend code, message, traceId
      const errorBanner = mountedWrapper().find('.banner.error')
      expect(errorBanner.exists()).toBe(true)
      expect(errorBanner.text()).toContain('VALIDATION_FAILED')
      expect(errorBanner.text()).toContain('Reply too long')
      expect(errorBanner.text()).toContain('Trace ID')
      expect(errorBanner.text()).toContain('trace-reply-err')

      // Textarea still contains user draft
      expect((textarea.element as HTMLTextAreaElement).value).toBe('Draft content to preserve')
    })

    it('reply save failure allows retry with second API call', async () => {
      vi.mocked(upsertAdminReviewReply)
        .mockRejectedValueOnce(new HttpClientError('First failure', { code: 'ERROR', status: 500, traceId: 'trace-reply-fail' }))
        .mockResolvedValueOnce({ data: createReview({ reviewId: 1, replyContent: 'Retry success' }), meta: mockMeta })

      await mountView()
      const row = mountedWrapper().findAll('.review-row')[0]
      const textarea = row.find('textarea')
      await textarea.setValue('Retry content')

      const rlAct = replyActions(0)

      // First submit - fails
      await rlAct[0].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(upsertAdminReviewReply)).toHaveBeenCalledTimes(1)

      // Second submit - succeeds
      await rlAct[0].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(upsertAdminReviewReply)).toHaveBeenCalledTimes(2)
    })
  })

  describe('reply visibility', () => {
    it('no reply disables both hide reply and restore reply buttons', async () => {
      await mountView()
      const rlAct = replyActions(0) // review 1 has no reply
      expect(rlAct[1].attributes('disabled')).toBeDefined()
      expect(rlAct[2].attributes('disabled')).toBeDefined()
    })

    it('existing visible reply enables hide reply and disables restore reply', async () => {
      await mountView()
      const rlAct = replyActions(2) // review 3 has visible reply
      expect(rlAct[1].attributes('disabled')).toBeUndefined()
      expect(rlAct[2].attributes('disabled')).toBeDefined()
    })

    it('existing hidden reply enables restore reply and disables hide reply', async () => {
      await mountView()
      const rlAct = replyActions(3) // review 4 has hidden reply
      expect(rlAct[1].attributes('disabled')).toBeDefined()
      expect(rlAct[2].attributes('disabled')).toBeUndefined()
    })

    it('hide reply click calls updateAdminReviewReplyVisibility with hidden and requestId', async () => {
      await mountView()
      const rlAct = replyActions(2) // review 3 has visible reply
      await rlAct[1].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewReplyVisibility)).toHaveBeenCalledWith(
        3,
        { visibility: 'hidden', requestId: '00000000-0000-0000-0000-000000000001' },
      )
    })

    it('duplicate reply visibility click while pending does not call API twice', async () => {
      const controlled = createControlledApiResponse()
      vi.mocked(updateAdminReviewReplyVisibility).mockReturnValue(controlled.promise as ReturnType<typeof updateAdminReviewReplyVisibility>)

      await mountView()
      const rlAct = replyActions(2) // review 3 has visible reply
      await rlAct[1].trigger('click')
      await nextTick()
      await rlAct[1].trigger('click')
      await nextTick()

      expect(vi.mocked(updateAdminReviewReplyVisibility)).toHaveBeenCalledTimes(1)

      controlled.resolve({ data: createReview({ reviewId: 3, replyContent: 'Thank you!', replyVisibilityStatus: 'hidden' }), meta: mockMeta })
      await flushPromises()
    })

    it('restore reply click calls updateAdminReviewReplyVisibility with visible and requestId', async () => {
      await mountView()
      const rlAct = replyActions(3) // review 4 has hidden reply
      await rlAct[2].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewReplyVisibility)).toHaveBeenCalledWith(
        4,
        { visibility: 'visible', requestId: '00000000-0000-0000-0000-000000000001' },
      )
    })

    it('reply visibility failure preserves row state and allows retry', async () => {
      vi.mocked(updateAdminReviewReplyVisibility)
        .mockRejectedValueOnce(new HttpClientError('Visibility failed', { code: 'ERROR', status: 500, traceId: 'trace-rv-err' }))
        .mockResolvedValueOnce({ data: createReview({ reviewId: 3, replyContent: 'Thank you!', replyVisibilityStatus: 'hidden' }), meta: mockMeta })

      await mountView()
      const rlAct = replyActions(2) // review 3 has visible reply

      // Click hide reply - fails
      await rlAct[1].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewReplyVisibility)).toHaveBeenCalledTimes(1)

      // Reply label/state unchanged - still visible
      expect(rlAct[1].attributes('disabled')).toBeUndefined()
      expect(rlAct[2].attributes('disabled')).toBeDefined()

      // Retry - succeeds
      await rlAct[1].trigger('click')
      await flushPromises()
      await nextTick()

      expect(vi.mocked(updateAdminReviewReplyVisibility)).toHaveBeenCalledTimes(2)
    })
  })
})
