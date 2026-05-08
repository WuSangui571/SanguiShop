import type { ProductReviewPageResponse } from '../../types/api/product'
import { formatDateTime } from '../../utils/format'

export interface MallProductReviewLabels {
  empty: string
  summary: string
  ratingValue: string
  ratingDistribution: string
  pageSummary: string
  createdAt: string
  skuName: string
  user: string
  contentEmpty: string
  merchantReply: string
  merchantReplyAt: string
}

export interface MallProductReviewMerchantReplyView {
  content: string
  repliedAtLabel: string
}

export interface MallProductReviewItemView {
  reviewId: number
  ratingLabel: string
  content: string
  createdAtLabel: string
  skuNameLabel: string
  userLabel: string
  imageUrls: string[]
  merchantReply: MallProductReviewMerchantReplyView | null
}

export interface MallProductReviewDistributionView {
  rating: number
  label: string
  count: number
  percent: number
  percentLabel: string
  barStyle: string
}

export interface MallProductReviewView {
  summary: string
  isEmpty: boolean
  emptyMessage: string
  pageSummary: string
  distribution: MallProductReviewDistributionView[]
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
      pageSummary: createPageSummary(null, labels),
      distribution: createDistribution(response, labels),
      items: [],
    }
  }

  return {
    summary: labels.summary
      .replace('{average}', formatRating(response.averageRating))
      .replace('{count}', String(response.reviewCount)),
    isEmpty: response.items.length === 0,
    emptyMessage: labels.empty,
    pageSummary: createPageSummary(response, labels),
    distribution: createDistribution(response, labels),
    items: response.items.map((item) => ({
      reviewId: item.reviewId,
      ratingLabel: labels.ratingValue.replace('{rating}', String(item.rating)),
      content: item.content?.trim() || labels.contentEmpty,
      createdAtLabel: labels.createdAt.replace('{time}', formatDateTime(item.createdAt)),
      skuNameLabel: labels.skuName.replace('{skuName}', item.skuName || '--'),
      userLabel: labels.user.replace('{user}', item.maskedUserId || '***'),
      imageUrls: item.imageUrls ?? [],
      merchantReply: item.merchantReply
        ? {
            content: item.merchantReply.content,
            repliedAtLabel: labels.merchantReplyAt.replace('{time}', formatDateTime(item.merchantReply.repliedAt)),
          }
        : null,
    })),
  }
}

function createDistribution(
  response: ProductReviewPageResponse | null,
  labels: MallProductReviewLabels,
): MallProductReviewDistributionView[] {
  const total = response?.reviewCount ?? 0
  const distribution = response?.ratingDistribution ?? {}
  return [5, 4, 3, 2, 1].map((rating) => {
    const count = Number(distribution[String(rating)] ?? 0)
    const percent = total > 0 ? Math.round((count / total) * 100) : 0
    return {
      rating,
      label: labels.ratingDistribution.replace('{rating}', String(rating)),
      count,
      percent,
      percentLabel: `${percent}%`,
      barStyle: `width: ${percent}%`,
    }
  })
}

function createPageSummary(response: ProductReviewPageResponse | null, labels: MallProductReviewLabels): string {
  const page = response?.page ?? 1
  const size = response?.size ?? 0
  const total = response?.reviewCount ?? 0
  const totalPages = size > 0 ? Math.max(1, Math.ceil(total / size)) : 1
  return labels.pageSummary
    .replace('{page}', String(page))
    .replace('{totalPages}', String(totalPages))
    .replace('{total}', String(total))
    .replace('{size}', String(size))
}

function formatRating(value: number): string {
  if (!Number.isFinite(value)) {
    return '0.0'
  }
  return value.toFixed(1)
}
