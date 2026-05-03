import type {
  OrderCompensationAggregateResponse,
  OrderCompensationQueryRequest,
  OrderCompensationQueryResponse,
  PaymentCompensationAggregateResponse,
  PaymentCompensationQueryRequest,
  PaymentCompensationQueryResponse,
} from '../../types/api/compensation'
import { formatCount, formatDateTime } from '../../utils/format'

export type CompensationView = 'order' | 'payment'

export interface DashboardFilters {
  shopId: string
  orderId: string
  paymentNo: string
  trigger: string
  result: string
  operator: string
  traceId: string
  fromTime: string
  toTime: string
  pageNo: number
  pageSize: number
}

export interface SummaryCardModel {
  label: string
  value: string
  hint: string
  tone: 'default' | 'success' | 'warning' | 'danger'
}

export type DashboardResponse = OrderCompensationQueryResponse | PaymentCompensationQueryResponse
export type DashboardItem = OrderCompensationAggregateResponse | PaymentCompensationAggregateResponse

export const triggerOptions = [
  { label: 'All triggers', value: '' },
  { label: 'Manual', value: 'manual' },
  { label: 'Scheduler', value: 'scheduler' },
] as const

export const resultOptions = [
  { label: 'All results', value: '' },
  { label: 'Failed', value: 'failed' },
  { label: 'Skipped', value: 'skipped' },
  { label: 'Cancelled', value: 'cancelled' },
  { label: 'Settled', value: 'settled' },
] as const

export const pageSizeOptions = [10, 20, 50, 100] as const

export function createDefaultFilters(): DashboardFilters {
  return {
    shopId: String(import.meta.env.VITE_DEFAULT_SHOP_ID ?? '1'),
    orderId: '',
    paymentNo: '',
    trigger: '',
    result: '',
    operator: '',
    traceId: '',
    fromTime: '',
    toTime: '',
    pageNo: 1,
    pageSize: 20,
  }
}

export function buildOrderQuery(filters: DashboardFilters): OrderCompensationQueryRequest {
  return {
    shopId: parseRequiredNumber(filters.shopId, 1),
    orderId: parseOptionalNumber(filters.orderId),
    trigger: normalizeOptional(filters.trigger),
    result: normalizeOptional(filters.result),
    operator: normalizeOptional(filters.operator),
    traceId: normalizeOptional(filters.traceId),
    fromTime: toIsoDateTime(filters.fromTime),
    toTime: toIsoDateTime(filters.toTime),
    pageNo: filters.pageNo,
    pageSize: filters.pageSize,
  }
}

export function buildPaymentQuery(filters: DashboardFilters): PaymentCompensationQueryRequest {
  return {
    shopId: parseRequiredNumber(filters.shopId, 1),
    orderId: parseOptionalNumber(filters.orderId),
    paymentNo: normalizeOptional(filters.paymentNo),
    trigger: normalizeOptional(filters.trigger),
    result: normalizeOptional(filters.result),
    operator: normalizeOptional(filters.operator),
    traceId: normalizeOptional(filters.traceId),
    fromTime: toIsoDateTime(filters.fromTime),
    toTime: toIsoDateTime(filters.toTime),
    pageNo: filters.pageNo,
    pageSize: filters.pageSize,
  }
}

export function getAggregateKey(view: CompensationView, item: DashboardItem): string {
  if (view === 'order') {
    const orderItem = item as OrderCompensationAggregateResponse
    return `order-${orderItem.order.orderId}`
  }

  const paymentItem = item as PaymentCompensationAggregateResponse
  return `payment-${paymentItem.payment.paymentId}`
}

export function deriveSummaryCards(view: CompensationView, response: DashboardResponse | null): SummaryCardModel[] {
  const items = response?.items ?? []
  const failedCount = items.filter((item) => getLatestResult(item) === 'failed').length
  const manualCount = items.filter((item) => getLatestTrigger(item) === 'manual').length
  const latestAttemptCandidates = items
    .map((item) => item.latestAttemptAt)
    .filter((value): value is string => Boolean(value))
    .sort()
  const latestAttempt = latestAttemptCandidates.length > 0
    ? latestAttemptCandidates[latestAttemptCandidates.length - 1]
    : undefined

  const totalMatchedAttempts = items.reduce((sum, item) => sum + item.matchedAttemptCount, 0)

  return [
    {
      label: `${capitalize(view)} records`,
      value: formatCount(response?.total ?? 0),
      hint: 'Distinct business records returned by the query contract.',
      tone: 'default',
    },
    {
      label: 'Matched attempts',
      value: formatCount(totalMatchedAttempts),
      hint: 'Filtered attempt count across the current page.',
      tone: 'success',
    },
    {
      label: 'Latest failed',
      value: formatCount(failedCount),
      hint: 'Rows whose latest compensation result is currently failed.',
      tone: failedCount > 0 ? 'danger' : 'success',
    },
    {
      label: 'Manual latest',
      value: formatCount(manualCount),
      hint: latestAttempt ? `Most recent attempt at ${formatDateTime(latestAttempt)}.` : 'No attempt matched yet.',
      tone: manualCount > 0 ? 'warning' : 'default',
    },
  ]
}

export function getLastTraceId(item: DashboardItem): string | null {
  if ('order' in item) {
    return item.order.lastCompensationTraceId ?? item.order.traceId
  }

  return item.payment.lastCompensationTraceId ?? item.payment.traceId
}

export function getLatestResult(item: DashboardItem): string | null {
  if ('order' in item) {
    return item.order.lastCompensationResult
  }

  return item.payment.lastCompensationResult
}

export function getLatestTrigger(item: DashboardItem): string | null {
  if ('order' in item) {
    return item.order.lastCompensationTrigger
  }

  return item.payment.lastCompensationTrigger
}

export function humanizeCode(value: string | null | undefined): string {
  if (!value) {
    return 'Unknown'
  }

  return value
    .split(/[_-]/g)
    .filter(Boolean)
    .map((part) => capitalize(part.toLowerCase()))
    .join(' ')
}

function normalizeOptional(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
}

function parseOptionalNumber(value: string): number | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }

  const parsed = Number(trimmed)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function parseRequiredNumber(value: string, fallback: number): number {
  const parsed = parseOptionalNumber(value)
  return parsed ?? fallback
}

function toIsoDateTime(value: string): string | undefined {
  if (!value) {
    return undefined
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

function capitalize(value: string): string {
  if (!value) {
    return value
  }

  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`
}
