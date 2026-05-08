import { describe, expect, it } from 'vitest'
import { createMallProductReviewView } from '../src/views/mall/mallProductReviewModel'
import type { ProductReviewPageResponse } from '../src/types/api/product'

const labels = {
  empty: 'No reviews yet.',
  summary: '{average} average / {count} reviews',
  ratingValue: '{rating} stars',
  createdAt: 'Reviewed at {time}',
  skuName: 'SKU {skuName}',
  user: 'User {user}',
  contentEmpty: 'No review content was provided.',
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
    expect(view.items[1].content).toBe('No review content was provided.')
  })

  it('shows an empty state for products without reviews', () => {
    const view = createMallProductReviewView({
      productId: 301,
      averageRating: 0,
      reviewCount: 0,
      page: 1,
      size: 5,
      items: [],
    }, labels)

    expect(view.summary).toBe('0.0 average / 0 reviews')
    expect(view.isEmpty).toBe(true)
    expect(view.emptyMessage).toBe('No reviews yet.')
    expect(view.items).toEqual([])
  })
})

function createReviewPage(): ProductReviewPageResponse {
  return {
    productId: 301,
    averageRating: 4.5,
    reviewCount: 2,
    page: 1,
    size: 5,
    items: [
      {
        reviewId: 9001,
        rating: 5,
        content: 'Matched expectations.',
        imageUrls: [],
        createdAt: '2026-05-08T10:00:00+08:00',
        maskedUserId: '10***01',
        skuName: 'Size 42',
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
