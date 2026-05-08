import { describe, expect, it } from 'vitest'
import { buildProductReviewQuery } from '../src/services/productApi'
import { createMallProductReviewView } from '../src/views/mall/mallProductReviewModel'
import type { ProductReviewPageResponse } from '../src/types/api/product'

const labels = {
  empty: 'No reviews yet.',
  summary: '{average} average / {count} reviews',
  ratingValue: '{rating} stars',
  ratingDistribution: '{rating} stars',
  pageSummary: 'Page {page} of {totalPages}, {total} total, {size} per page',
  createdAt: 'Reviewed at {time}',
  skuName: 'SKU {skuName}',
  user: 'User {user}',
  contentEmpty: 'No review content was provided.',
  merchantReply: 'Merchant reply',
  merchantReplyAt: 'Replied at {time}',
}

describe('mall product review model', () => {
  it('formats rating summary, time, SKU name, and masked user display', () => {
    const view = createMallProductReviewView(createReviewPage(), labels)

    expect(view.summary).toBe('4.5 average / 2 reviews')
    expect(view.isEmpty).toBe(false)
    expect(view.items[0]).toMatchObject({
      ratingLabel: '5 stars',
      content: 'Matched expectations.',
      skuNameLabel: 'SKU Size 42',
      userLabel: 'User 10***01',
    })
    expect(view.items[0].createdAtLabel).toContain('Reviewed at')
    expect(view.items[0].merchantReply?.content).toBe('Thanks for the feedback.')
    expect(view.items[0].merchantReply?.repliedAtLabel).toContain('Replied at')
    expect(view.items[0].imageUrls).toEqual(['https://cdn.example/review.jpg'])
    expect(view.distribution[0]).toMatchObject({
      rating: 5,
      count: 1,
      percent: 50,
      percentLabel: '50%',
      barStyle: 'width: 50%',
    })
    expect(view.pageSummary).toBe('Page 1 of 1, 2 total, 5 per page')
    expect(view.items[1].content).toBe('No review content was provided.')
    expect(view.items[1].merchantReply).toBeNull()
  })

  it('shows an empty state for products without reviews', () => {
    const view = createMallProductReviewView({
      productId: 301,
      averageRating: 0,
      reviewCount: 0,
      ratingDistribution: {},
      page: 1,
      size: 5,
      items: [],
    }, labels)

    expect(view.summary).toBe('0.0 average / 0 reviews')
    expect(view.isEmpty).toBe(true)
    expect(view.emptyMessage).toBe('No reviews yet.')
    expect(view.distribution.every((item) => item.count === 0 && item.percent === 0)).toBe(true)
    expect(view.items).toEqual([])
  })

  it('builds product review query payload with optional image-only filter', () => {
    expect(buildProductReviewQuery({ page: 2, size: 5, withImages: true })).toEqual({
      page: 2,
      size: 5,
      withImages: true,
    })
    expect(buildProductReviewQuery({ page: 1, size: 10, withImages: false })).toEqual({
      page: 1,
      size: 10,
    })
  })
})

function createReviewPage(): ProductReviewPageResponse {
  return {
    productId: 301,
    averageRating: 4.5,
    reviewCount: 2,
    ratingDistribution: {
      1: 0,
      2: 0,
      3: 0,
      4: 1,
      5: 1,
    },
    page: 1,
    size: 5,
    items: [
      {
        reviewId: 9001,
        rating: 5,
        content: 'Matched expectations.',
        imageUrls: ['https://cdn.example/review.jpg'],
        createdAt: '2026-05-08T10:00:00+08:00',
        maskedUserId: '10***01',
        skuName: 'Size 42',
        merchantReply: {
          content: 'Thanks for the feedback.',
          repliedAt: '2026-05-08T12:00:00+08:00',
        },
      },
      {
        reviewId: 9002,
        rating: 4,
        content: null,
        imageUrls: [],
        createdAt: '2026-05-08T11:00:00+08:00',
        maskedUserId: '10***02',
        skuName: 'Size 43',
      },
    ],
  }
}
