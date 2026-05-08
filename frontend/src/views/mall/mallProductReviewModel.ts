import type { ProductReviewPageResponse } from '../../types/api/product'
import { formatDateTime } from '../../utils/format'

export interface MallProductReviewLabels {
  empty: string
  summary: string
  ratingValue: string
  createdAt: string
  skuName: string
  user: string
  contentEmpty: string
}

export interface MallProductReviewItemView {
  reviewId: number
  ratingLabel: string
  content: string
  createdAtLabel: string
  skuNameLabel: string
  userLabel: string
}

export interface MallProductReviewView {
  summary: string
  isEmpty: boolean
  emptyMessage: string
  items: MallProductReviewItemView[]
}

export function createMallProductReviewView(
  response: ProductReviewPageResponse | null,
  labels: MallProductReviewLabels,
): MallProductReviewView {
  if (!response || response.reviewCount <= 0) {
    return {
      summary: labels.summary
        .replace('{average}', '0.0')
        .replace('{count}', '0'),
      isEmpty: true,
      emptyMessage: labels.empty,
      items: [],
    }
  }

  return {
    summary: labels.summary
      .replace('{average}', formatRating(response.averageRating))
      .replace('{count}', String(response.reviewCount)),
    isEmpty: response.items.length === 0,
    emptyMessage: labels.empty,
    items: response.items.map((item) => ({
      reviewId: item.reviewId,
      ratingLabel: labels.ratingValue.replace('{rating}', String(item.rating)),
      content: item.content?.trim() || labels.contentEmpty,
      createdAtLabel: labels.createdAt.replace('{time}', formatDateTime(item.createdAt)),
      skuNameLabel: labels.skuName.replace('{skuName}', item.skuName || '--'),
      userLabel: labels.user.replace('{user}', item.maskedUserId || '***'),
    })),
  }
}

function formatRating(value: number): string {
  if (!Number.isFinite(value)) {
    return '0.0'
  }
  return value.toFixed(1)
}
