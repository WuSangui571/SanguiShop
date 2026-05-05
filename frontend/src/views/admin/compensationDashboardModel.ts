import type { HttpClientError } from '../../services/httpClient'
import type {
  BulkOrderTimeoutReplayRequest,
  BulkOrderTimeoutReplayResponse,
  BulkPaymentReconcileRequest,
  BulkPaymentReconcileResponse,
  ManualOrderTimeoutReplayRequest,
  ManualPaymentReconcileRequest,
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

export interface ReplayControls {
  operator: string
  dryRun: boolean
  bulkLimit: number
}

export interface DashboardStateSnapshot {
  view: CompensationView
  filters: DashboardFilters
  replayControls: ReplayControls
}

export interface SummaryCardModel {
  label: string
  value: string
  hint: string
  tone: 'default' | 'success' | 'warning' | 'danger'
}

export interface ExportRow {
  kind: CompensationView
  businessKey: string
  status: string
  latestResult: string
  latestTrigger: string
  latestOperator: string
  latestTraceId: string
  latestAttemptAt: string
  matchedAttemptCount: string
  totalAttemptCount: string
}

export type DashboardResponse = OrderCompensationQueryResponse | PaymentCompensationQueryResponse
export type DashboardItem = OrderCompensationAggregateResponse | PaymentCompensationAggregateResponse

interface DashboardStatePatch {
  view?: CompensationView
  filters?: Partial<DashboardFilters>
  replayControls?: Partial<ReplayControls>
}

const DEFAULT_PAGE_NO = 1
const DEFAULT_PAGE_SIZE = 20
const DEFAULT_BULK_LIMIT = 20
const DEFAULT_LOOKBACK_HOURS = 24

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

export function createDefaultFilters(now: Date = new Date()): DashboardFilters {
  return {
    shopId: String(import.meta.env.VITE_DEFAULT_SHOP_ID ?? '1'),
    orderId: '',
    paymentNo: '',
    trigger: '',
    result: '',
    operator: '',
    traceId: '',
    fromTime: toDateTimeLocal(new Date(now.getTime() - DEFAULT_LOOKBACK_HOURS * 60 * 60 * 1000)),
    toTime: toDateTimeLocal(now),
    pageNo: DEFAULT_PAGE_NO,
    pageSize: DEFAULT_PAGE_SIZE,
  }
}

export function createDefaultReplayControls(): ReplayControls {
  return {
    operator: '',
    dryRun: true,
    bulkLimit: DEFAULT_BULK_LIMIT,
  }
}

export function createDefaultDashboardState(now: Date = new Date()): DashboardStateSnapshot {
  return {
    view: 'payment',
    filters: createDefaultFilters(now),
    replayControls: createDefaultReplayControls(),
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

export function buildOrderManualReplayRequest(
  filters: DashboardFilters,
  item: OrderCompensationAggregateResponse,
  replayControls: ReplayControls,
): ManualOrderTimeoutReplayRequest {
  return {
    shopId: parseRequiredNumber(filters.shopId, 1),
    orderId: item.order.orderId,
    operator: requireReplayOperator(replayControls.operator),
  }
}

export function buildPaymentManualReplayRequest(
  filters: DashboardFilters,
  item: PaymentCompensationAggregateResponse,
  replayControls: ReplayControls,
): ManualPaymentReconcileRequest {
  return {
    shopId: parseRequiredNumber(filters.shopId, 1),
    paymentNo: item.payment.paymentNo,
    operator: requireReplayOperator(replayControls.operator),
  }
}

export function buildOrderBulkReplayRequest(
  filters: DashboardFilters,
  items: OrderCompensationAggregateResponse[],
  replayControls: ReplayControls,
): BulkOrderTimeoutReplayRequest {
  return {
    shopId: parseRequiredNumber(filters.shopId, 1),
    dryRun: replayControls.dryRun,
    operator: requireReplayOperator(replayControls.operator),
    limit: normalizeBulkLimit(replayControls.bulkLimit, items.length),
    orderIds: items.slice(0, normalizeBulkLimit(replayControls.bulkLimit, items.length)).map((item) => item.order.orderId),
  }
}

export function buildPaymentBulkReplayRequest(
  filters: DashboardFilters,
  items: PaymentCompensationAggregateResponse[],
  replayControls: ReplayControls,
): BulkPaymentReconcileRequest {
  return {
    shopId: parseRequiredNumber(filters.shopId, 1),
    dryRun: replayControls.dryRun,
    operator: requireReplayOperator(replayControls.operator),
    limit: normalizeBulkLimit(replayControls.bulkLimit, items.length),
    paymentNos: items.slice(0, normalizeBulkLimit(replayControls.bulkLimit, items.length)).map((item) => item.payment.paymentNo),
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

export function getLatestOperator(item: DashboardItem): string | null {
  if ('order' in item) {
    return item.order.lastCompensationOperator
  }

  return item.payment.lastCompensationOperator
}

export function getDashboardItemLabel(view: CompensationView, item: DashboardItem): string {
  if (view === 'order') {
    return (item as OrderCompensationAggregateResponse).order.orderNo
  }

  return (item as PaymentCompensationAggregateResponse).payment.paymentNo
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

export function describeDashboardError(error: HttpClientError): string {
  if (error.code === 'AUTH_TOKEN_EXPIRED') {
    return 'Session expired. Sign in again and retry the compensation operation.'
  }
  if (error.code === 'AUTH_FORBIDDEN') {
    return 'Current session is authenticated but lacks compensation operations access.'
  }
  if (error.code === 'AUTH_TOKEN_MISSING' || error.code === 'SIGNATURE_INVALID') {
    return 'A valid compensation ops session is required for this gateway-backed dashboard.'
  }
  if (error.code === 'VALIDATION_FAILED') {
    return 'The request was rejected by validation. Check filters, operator, and replay scope.'
  }

  return error.message
}

export function buildDashboardSearchParams(state: DashboardStateSnapshot): URLSearchParams {
  const params = new URLSearchParams()
  params.set('view', state.view)
  setIfPresent(params, 'shopId', state.filters.shopId)
  setIfPresent(params, 'orderId', state.filters.orderId)
  setIfPresent(params, 'paymentNo', state.filters.paymentNo)
  setIfPresent(params, 'trigger', state.filters.trigger)
  setIfPresent(params, 'result', state.filters.result)
  setIfPresent(params, 'operator', state.filters.operator)
  setIfPresent(params, 'traceId', state.filters.traceId)
  setIfPresent(params, 'from', state.filters.fromTime)
  setIfPresent(params, 'to', state.filters.toTime)
  params.set('pageNo', String(state.filters.pageNo))
  params.set('pageSize', String(state.filters.pageSize))
  setIfPresent(params, 'replayOperator', state.replayControls.operator)
  params.set('dryRun', String(state.replayControls.dryRun))
  params.set('bulkLimit', String(state.replayControls.bulkLimit))
  return params
}

export function serializeDashboardState(state: DashboardStateSnapshot): string {
  return JSON.stringify({
    version: 1,
    ...state,
  })
}

export function deserializeDashboardState(serialized: string | null, now: Date = new Date()): DashboardStateSnapshot | null {
  if (!serialized) {
    return null
  }

  try {
    const parsed = JSON.parse(serialized) as DashboardStatePatch & { version?: number }
    if (parsed.version !== 1) {
      return null
    }

    return mergeDashboardState(createDefaultDashboardState(now), parsed)
  } catch {
    return null
  }
}

export function readDashboardStateFromSearch(search: string, now: Date = new Date()): DashboardStateSnapshot | null {
  const params = new URLSearchParams(search)
  if (!params.has('view') && !params.has('shopId') && !params.has('replayOperator')) {
    return null
  }

  const patch: DashboardStatePatch = {
    view: normalizeView(params.get('view')),
    filters: {
      shopId: params.get('shopId') ?? undefined,
      orderId: params.get('orderId') ?? undefined,
      paymentNo: params.get('paymentNo') ?? undefined,
      trigger: params.get('trigger') ?? undefined,
      result: params.get('result') ?? undefined,
      operator: params.get('operator') ?? undefined,
      traceId: params.get('traceId') ?? undefined,
      fromTime: params.get('from') ?? undefined,
      toTime: params.get('to') ?? undefined,
      pageNo: parsePositiveInt(params.get('pageNo')),
      pageSize: parsePositiveInt(params.get('pageSize')),
    } as Partial<DashboardFilters>,
    replayControls: {
      operator: params.get('replayOperator') ?? undefined,
      dryRun: parseBoolean(params.get('dryRun')),
      bulkLimit: parsePositiveInt(params.get('bulkLimit')),
    },
  }

  return mergeDashboardState(createDefaultDashboardState(now), patch)
}

export function buildExportRows(view: CompensationView, items: DashboardItem[]): ExportRow[] {
  return items.map((item) => {
    if (view === 'order') {
      const orderItem = item as OrderCompensationAggregateResponse
      return {
        kind: 'order',
        businessKey: orderItem.order.orderNo,
        status: orderItem.order.status,
        latestResult: orderItem.order.lastCompensationResult ?? '',
        latestTrigger: orderItem.order.lastCompensationTrigger ?? '',
        latestOperator: orderItem.order.lastCompensationOperator ?? '',
        latestTraceId: orderItem.order.lastCompensationTraceId ?? orderItem.order.traceId ?? '',
        latestAttemptAt: orderItem.latestAttemptAt ?? '',
        matchedAttemptCount: String(orderItem.matchedAttemptCount),
        totalAttemptCount: String(orderItem.totalAttemptCount),
      }
    }

    const paymentItem = item as PaymentCompensationAggregateResponse
    return {
      kind: 'payment',
      businessKey: paymentItem.payment.paymentNo,
      status: paymentItem.payment.status,
      latestResult: paymentItem.payment.lastCompensationResult ?? '',
      latestTrigger: paymentItem.payment.lastCompensationTrigger ?? '',
      latestOperator: paymentItem.payment.lastCompensationOperator ?? '',
      latestTraceId: paymentItem.payment.lastCompensationTraceId ?? paymentItem.payment.traceId ?? '',
      latestAttemptAt: paymentItem.latestAttemptAt ?? '',
      matchedAttemptCount: String(paymentItem.matchedAttemptCount),
      totalAttemptCount: String(paymentItem.totalAttemptCount),
    }
  })
}

export function summarizeBulkReplay(response: BulkOrderTimeoutReplayResponse | BulkPaymentReconcileResponse): string {
  return `matched ${response.matchedCount}, executed ${response.executedCount}, success ${response.successCount}, skipped ${response.skippedCount}, failed ${response.failedCount}`
}

function mergeDashboardState(
  defaults: DashboardStateSnapshot,
  partial: DashboardStatePatch,
): DashboardStateSnapshot {
  const partialFilters: Partial<DashboardFilters> = partial.filters ?? {}
  const partialReplayControls: Partial<ReplayControls> = partial.replayControls ?? {}

  return {
    view: partial.view ?? defaults.view,
    filters: {
      shopId: partialFilters.shopId ?? defaults.filters.shopId,
      orderId: partialFilters.orderId ?? defaults.filters.orderId,
      paymentNo: partialFilters.paymentNo ?? defaults.filters.paymentNo,
      trigger: partialFilters.trigger ?? defaults.filters.trigger,
      result: partialFilters.result ?? defaults.filters.result,
      operator: partialFilters.operator ?? defaults.filters.operator,
      traceId: partialFilters.traceId ?? defaults.filters.traceId,
      fromTime: partialFilters.fromTime ?? defaults.filters.fromTime,
      toTime: partialFilters.toTime ?? defaults.filters.toTime,
      pageNo: parsePositiveInt((partialFilters.pageNo ?? defaults.filters.pageNo).toString()) ?? defaults.filters.pageNo,
      pageSize: parsePositiveInt((partialFilters.pageSize ?? defaults.filters.pageSize).toString()) ?? defaults.filters.pageSize,
    },
    replayControls: {
      operator: partialReplayControls.operator ?? defaults.replayControls.operator,
      dryRun: partialReplayControls.dryRun ?? defaults.replayControls.dryRun,
      bulkLimit: parsePositiveInt((partialReplayControls.bulkLimit ?? defaults.replayControls.bulkLimit).toString())
        ?? defaults.replayControls.bulkLimit,
    },
  }
}

function setIfPresent(params: URLSearchParams, key: string, value: string) {
  const trimmed = value.trim()
  if (trimmed) {
    params.set(key, trimmed)
  }
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

function parsePositiveInt(value: string | null): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

function parseBoolean(value: string | null): boolean | undefined {
  if (value === 'true') {
    return true
  }
  if (value === 'false') {
    return false
  }

  return undefined
}

function toIsoDateTime(value: string): string | undefined {
  if (!value) {
    return undefined
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

function toDateTimeLocal(value: Date): string {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  const hour = String(value.getHours()).padStart(2, '0')
  const minute = String(value.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function normalizeView(value: string | null): CompensationView | undefined {
  if (value === 'order' || value === 'payment') {
    return value
  }

  return undefined
}

function requireReplayOperator(value: string): string {
  const trimmed = value.trim()
  return trimmed
}

function normalizeBulkLimit(value: number, availableCount: number): number {
  const safeValue = Number.isFinite(value) && value > 0 ? Math.floor(value) : DEFAULT_BULK_LIMIT
  const safeAvailable = Math.max(1, availableCount)
  return Math.min(safeValue, safeAvailable)
}

function capitalize(value: string): string {
  if (!value) {
    return value
  }

  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`
}
