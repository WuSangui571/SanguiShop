import { describe, expect, it } from 'vitest'
import { HttpClientError } from '../../services/httpClient'
import {
  buildAdminReviewQuery,
  buildAdminReviewImageError,
  buildAdminReviewImageView,
  buildAdminReviewReplyRequest,
  buildAdminReviewReplyVisibilityRequest,
  buildAdminReviewVisibilityRequest,
  canHideAdminReviewReply,
  canHideAdminReview,
  canRestoreAdminReviewReply,
  canRestoreAdminReview,
  createDefaultReviewFilters,
  createSubmissionGate,
  deserializeAdminReviewFilters,
  getAdminReviewReplyLabel,
  getAdminReviewVisibilityLabel,
  replaceAdminReviewItem,
  serializeAdminReviewFilters,
  toAdminReviewError,
} from './reviewManagementModel'
import type { AdminReviewSummaryResponse } from '../../types/api/order'

describe('reviewManagementModel', () => {
  it('builds trimmed filter payload and omits all or blank filters', () => {
    const filters = createDefaultReviewFilters()
    filters.productId = ' 301 '
    filters.rating = '5'
    filters.userId = ' 10001 '
    filters.visibility = 'all'
    filters.fromTime = '2026-05-08T10:00'
    filters.toTime = '2026-05-09T10:00:00+08:00'

    expect(buildAdminReviewQuery(filters)).toEqual({
      page: 1,
      size: 20,
      productId: 301,
      rating: 5,
      userId: '10001',
      fromTime: '2026-05-08T10:00:00+08:00',
      toTime: '2026-05-09T10:00:00+08:00',
    })
  })

  it('drops invalid numeric filters before sending query params', () => {
    const filters = createDefaultReviewFilters()
    filters.productId = '0'
    filters.rating = '6'
    filters.visibility = 'hidden'
    filters.page = -10
    filters.size = 500

    expect(buildAdminReviewQuery(filters)).toEqual({
      page: 1,
      size: 100,
      visibility: 'hidden',
    })
  })

  it('builds trimmed visibility request payload', () => {
    expect(buildAdminReviewVisibilityRequest('hidden', ' sensitive ', ' req-1 ')).toEqual({
      visibility: 'hidden',
      reason: 'sensitive',
      requestId: 'req-1',
    })
    expect(buildAdminReviewVisibilityRequest('visible', ' ', ' req-2 ')).toEqual({
      visibility: 'visible',
      reason: null,
      requestId: 'req-2',
    })
  })

  it('builds trimmed reply request payloads', () => {
    expect(buildAdminReviewReplyRequest(' Thanks ', ' reply-1 ')).toEqual({
      content: 'Thanks',
      requestId: 'reply-1',
    })
    expect(buildAdminReviewReplyVisibilityRequest('hidden', ' reply-vis-1 ')).toEqual({
      visibility: 'hidden',
      requestId: 'reply-vis-1',
    })
  })

  it('labels visibility values and preserves unknown fallback', () => {
    const labels = {
      visible: 'Visible',
      hidden: 'Hidden',
    }

    expect(getAdminReviewVisibilityLabel('visible', labels)).toBe('Visible')
    expect(getAdminReviewVisibilityLabel('hidden', labels)).toBe('Hidden')
    expect(getAdminReviewVisibilityLabel('archived', labels)).toBe('archived')
  })

  it('labels reply state and preserves unknown fallback', () => {
    const labels = {
      visible: 'Reply visible',
      hidden: 'Reply hidden',
      none: 'No reply',
    }

    expect(getAdminReviewReplyLabel(review({ replyContent: null }), labels)).toBe('No reply')
    expect(getAdminReviewReplyLabel(review({ replyContent: 'Thanks', replyVisibilityStatus: 'visible' }), labels)).toBe('Reply visible')
    expect(getAdminReviewReplyLabel(review({ replyContent: 'Thanks', replyVisibilityStatus: 'hidden' }), labels)).toBe('Reply hidden')
    expect(getAdminReviewReplyLabel(review({ replyContent: 'Thanks', replyVisibilityStatus: 'archived' }), labels)).toBe('archived')
  })

  it('builds admin review image list and unknown fallback from compatible payloads', () => {
    expect(buildAdminReviewImageView(review({
      imageCount: 3,
      imageUrls: [
        ' /api/uploads/review-images/review-a.jpg ',
        '',
        '/api/uploads/review-images/review-b.webp',
      ],
    }))).toEqual({
      urls: [
        '/api/uploads/review-images/review-a.jpg',
        '/api/uploads/review-images/review-b.webp',
      ],
      unknownCount: 1,
    })

    expect(buildAdminReviewImageView(review({ imageCount: 2, imageUrls: undefined }))).toEqual({
      urls: [],
      unknownCount: 2,
    })
  })

  it('builds traceable image load error state without blocking review governance', () => {
    expect(buildAdminReviewImageError(
      review({ reviewId: 42 }),
      '/api/uploads/review-images/missing.jpg',
    )).toEqual({
      code: 'REVIEW_IMAGE_LOAD_FAILED',
      message: 'Review 42 image failed to load.',
      reviewId: 42,
      url: '/api/uploads/review-images/missing.jpg',
    })
  })

  it('detects hide and restore action availability', () => {
    expect(canHideAdminReview(review({ visibilityStatus: 'visible' }))).toBe(true)
    expect(canHideAdminReview(review({ visibilityStatus: 'hidden' }))).toBe(false)
    expect(canRestoreAdminReview(review({ visibilityStatus: 'hidden' }))).toBe(true)
    expect(canRestoreAdminReview(review({ visibilityStatus: 'visible' }))).toBe(false)
    expect(canHideAdminReviewReply(review({ replyContent: 'Thanks', replyVisibilityStatus: 'visible' }))).toBe(true)
    expect(canHideAdminReviewReply(review({ replyContent: null, replyVisibilityStatus: 'visible' }))).toBe(false)
    expect(canRestoreAdminReviewReply(review({ replyContent: 'Thanks', replyVisibilityStatus: 'hidden' }))).toBe(true)
    expect(canRestoreAdminReviewReply(review({ replyContent: 'Thanks', replyVisibilityStatus: 'visible' }))).toBe(false)
  })

  it('replaces updated review item in current page', () => {
    const items = [
      review({ reviewId: 1, visibilityStatus: 'visible' }),
      review({ reviewId: 2, visibilityStatus: 'visible' }),
    ]
    const next = review({ reviewId: 2, visibilityStatus: 'hidden' })

    expect(replaceAdminReviewItem(items, next)).toEqual([
      items[0],
      next,
    ])
  })

  it('serializes and restores review filters from session storage payload', () => {
    const filters = createDefaultReviewFilters()
    filters.productId = '301'
    filters.rating = '4'
    filters.visibility = 'hidden'
    filters.page = 3

    expect(deserializeAdminReviewFilters(serializeAdminReviewFilters(filters))).toEqual(filters)
    expect(deserializeAdminReviewFilters('{"version":2}')).toBeNull()
    expect(deserializeAdminReviewFilters('not-json')).toBeNull()
  })

  it('preserves backend error code message and trace id', () => {
    const error = new HttpClientError('Forbidden', {
      code: 'AUTH_FORBIDDEN',
      status: 403,
      traceId: 'trace-review',
    })

    expect(toAdminReviewError(error, 'fallback')).toEqual({
      code: 'AUTH_FORBIDDEN',
      message: 'Forbidden',
      traceId: 'trace-review',
    })
  })

  it('guards duplicate pending actions', () => {
    const gate = createSubmissionGate()

    expect(gate.begin()).toBe(true)
    expect(gate.begin()).toBe(false)
    gate.end()
    expect(gate.begin()).toBe(true)
  })
})

function review(patch: Partial<AdminReviewSummaryResponse> = {}): AdminReviewSummaryResponse {
  return {
    reviewId: 1,
    orderId: 101,
    orderNo: 'ORD-101',
    productId: 301,
    skuId: 401,
    skuName: 'Sneaker 42',
    rating: 5,
    content: 'Great',
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
