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

import { listAdminReviews } from '../../services/orderApi'

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

async function mountView() {
  const w = mount(ReviewManagementView, {
    props: {
      session: mockSession,
      canAccessReviewWorkspace: true,
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
