import { HttpClientError } from '../../services/httpClient'
import type {
  AdminReviewQueryParams,
  AdminReviewReplyRequest,
  AdminReviewReplyVisibilityRequest,
  AdminReviewSummaryResponse,
  AdminReviewVisibilityFilter,
  AdminReviewVisibilityRequest,
} from '../../types/api/order'
import { createSubmissionGate } from './orderManagementModel'

export interface AdminReviewErrorState {
  code: string
  message: string
  traceId: string | null
}

export interface AdminReviewFilterDraft {
  productId: string
  rating: string
  userId: string
  visibility: AdminReviewVisibilityFilter
  fromTime: string
  toTime: string
  page: number
  size: number
}

export interface AdminReviewVisibilityLabels {
  visible: string
  hidden: string
}

export interface AdminReviewReplyLabels {
  visible: string
  hidden: string
  none: string
}

export const ADMIN_REVIEW_FILTER_STORAGE_KEY = 'sangui.admin.review.filters.v1'

export function createDefaultReviewFilters(): AdminReviewFilterDraft {
  return {
    productId: '',
    rating: '',
    userId: '',
    visibility: 'all',
    fromTime: '',
    toTime: '',
    page: 1,
    size: 20,
  }
}

export function buildAdminReviewQuery(filters: AdminReviewFilterDraft): AdminReviewQueryParams {
  const query: AdminReviewQueryParams = {
    page: normalizePage(filters.page),
    size: normalizeSize(filters.size),
  }
  const productId = parsePositiveInt(filters.productId)
  if (productId !== null) {
    query.productId = productId
  }
  const rating = parseRating(filters.rating)
  if (rating !== null) {
    query.rating = rating
  }
  const userId = filters.userId.trim()
  if (userId) {
    query.userId = userId
  }
  const visibility = filters.visibility.trim()
  if (visibility && visibility !== 'all') {
    query.visibility = visibility
  }
  const fromTime = filters.fromTime.trim()
  if (fromTime) {
    query.fromTime = normalizeDateTimeFilter(fromTime)
  }
  const toTime = filters.toTime.trim()
  if (toTime) {
    query.toTime = normalizeDateTimeFilter(toTime)
  }
  return query
}

export function buildAdminReviewVisibilityRequest(
  visibility: Exclude<AdminReviewVisibilityFilter, 'all'>,
  reason: string,
  requestId: string,
): AdminReviewVisibilityRequest {
  return {
    visibility: visibility.trim(),
    reason: reason.trim() || null,
    requestId: requestId.trim(),
  }
}

export function buildAdminReviewReplyRequest(content: string, requestId: string): AdminReviewReplyRequest {
  return {
    content: content.trim(),
    requestId: requestId.trim(),
  }
}

export function buildAdminReviewReplyVisibilityRequest(
  visibility: Exclude<AdminReviewVisibilityFilter, 'all'>,
  requestId: string,
): AdminReviewReplyVisibilityRequest {
  return {
    visibility: visibility.trim(),
    requestId: requestId.trim(),
  }
}

export function getAdminReviewVisibilityLabel(
  visibility: string,
  labels: AdminReviewVisibilityLabels,
): string {
  if (visibility === 'visible') {
    return labels.visible
  }
  if (visibility === 'hidden') {
    return labels.hidden
  }
  return visibility
}

export function getAdminReviewReplyLabel(
  item: AdminReviewSummaryResponse,
  labels: AdminReviewReplyLabels,
): string {
  if (!item.replyContent?.trim()) {
    return labels.none
  }
  if (item.replyVisibilityStatus === 'visible') {
    return labels.visible
  }
  if (item.replyVisibilityStatus === 'hidden') {
    return labels.hidden
  }
  return item.replyVisibilityStatus
}

export function replaceAdminReviewItem(
  items: AdminReviewSummaryResponse[],
  nextItem: AdminReviewSummaryResponse,
): AdminReviewSummaryResponse[] {
  return items.map((item) => (item.reviewId === nextItem.reviewId ? nextItem : item))
}

export function canHideAdminReview(item: AdminReviewSummaryResponse): boolean {
  return item.visibilityStatus !== 'hidden'
}

export function canRestoreAdminReview(item: AdminReviewSummaryResponse): boolean {
  return item.visibilityStatus === 'hidden'
}

export function canHideAdminReviewReply(item: AdminReviewSummaryResponse): boolean {
  return Boolean(item.replyContent?.trim()) && item.replyVisibilityStatus !== 'hidden'
}

export function canRestoreAdminReviewReply(item: AdminReviewSummaryResponse): boolean {
  return Boolean(item.replyContent?.trim()) && item.replyVisibilityStatus === 'hidden'
}

export function toAdminReviewError(
  caught: unknown,
  fallback: string,
  fallbackCode = 'UNEXPECTED_ERROR',
): AdminReviewErrorState {
  if (caught instanceof HttpClientError) {
    return {
      code: caught.code,
      message: caught.message,
      traceId: caught.traceId,
    }
  }

  return {
    code: fallbackCode,
    message: fallback,
    traceId: null,
  }
}

export function serializeAdminReviewFilters(filters: AdminReviewFilterDraft): string {
  return JSON.stringify({
    version: 1,
    filters: {
      productId: filters.productId,
      rating: filters.rating,
      userId: filters.userId,
      visibility: filters.visibility,
      fromTime: filters.fromTime,
      toTime: filters.toTime,
      page: normalizePage(filters.page),
      size: normalizeSize(filters.size),
    },
  })
}

export function deserializeAdminReviewFilters(serialized: string | null): AdminReviewFilterDraft | null {
  if (!serialized) {
    return null
  }

  try {
    const parsed = JSON.parse(serialized) as {
      version?: number
      filters?: Partial<AdminReviewFilterDraft>
    }
    if (parsed.version !== 1 || !parsed.filters) {
      return null
    }
    return mergeAdminReviewFilters(createDefaultReviewFilters(), parsed.filters)
  } catch {
    return null
  }
}

export function mergeAdminReviewFilters(
  defaults: AdminReviewFilterDraft,
  patch: Partial<AdminReviewFilterDraft>,
): AdminReviewFilterDraft {
  return {
    productId: patch.productId ?? defaults.productId,
    rating: patch.rating ?? defaults.rating,
    userId: patch.userId ?? defaults.userId,
    visibility: normalizeVisibilityFilter(patch.visibility) ?? defaults.visibility,
    fromTime: patch.fromTime ?? defaults.fromTime,
    toTime: patch.toTime ?? defaults.toTime,
    page: normalizePage(patch.page ?? defaults.page),
    size: normalizeSize(patch.size ?? defaults.size),
  }
}

export { createSubmissionGate }

function normalizePage(value: number): number {
  if (!Number.isFinite(value)) {
    return 1
  }
  return Math.max(1, Math.trunc(value))
}

function normalizeSize(value: number): number {
  if (!Number.isFinite(value)) {
    return 20
  }
  return Math.min(100, Math.max(1, Math.trunc(value)))
}

function normalizeDateTimeFilter(value: string): string {
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
    return `${value}:00+08:00`
  }
  return value
}

function normalizeVisibilityFilter(value: string | undefined): AdminReviewVisibilityFilter | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

function parsePositiveInt(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function parseRating(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 5 ? parsed : null
}
