import { HttpClientError } from '../../services/httpClient'
import type {
  AdminCancelOrderRequest,
  AdminOrderDetailResponse,
  AdminOrderQueryParams,
  AdminOrderStatusTimelineResponse,
  AdminOrderSummaryResponse,
  AdminOrderStatusFilter,
  OrderStatus,
} from '../../types/api/order'
import type { PaymentResponse } from '../../types/api/payment'

export interface AdminOrderErrorState {
  code: string
  message: string
  traceId: string | null
}

export interface AdminOrderFilterDraft {
  status: AdminOrderStatusFilter
  orderNo: string
  userId: string
  fromTime: string
  toTime: string
  page: number
  size: number
}

export interface AdminOrderStatusLabels {
  created: string
  paid: string
  cancelled: string
  shipped?: string
  completed?: string
}

export interface AdminOrderTimelineLabels {
  created: string
  paid: string
  cancelled: string
  shipped: string
  completed: string
  unknown: string
}

export interface AdminOrderTimelineView {
  status: OrderStatus
  statusLabel: string
  occurredAt: string | null
  traceId: string | null
  description: string
}

interface AdminOrderFilterPatch {
  status?: string
  orderNo?: string
  userId?: string
  fromTime?: string
  toTime?: string
  page?: number
  size?: number
}

interface PersistedAdminOrderFilters {
  version?: number
  filters?: AdminOrderFilterPatch
}

export const ADMIN_ORDER_FILTER_STORAGE_KEY = 'sangui.admin.order.filters.v1'

export function createDefaultOrderFilters(): AdminOrderFilterDraft {
  return {
    status: 'all',
    orderNo: '',
    userId: '',
    fromTime: '',
    toTime: '',
    page: 1,
    size: 20,
  }
}

export function buildAdminOrderQuery(filters: AdminOrderFilterDraft): AdminOrderQueryParams {
  const query: AdminOrderQueryParams = {
    page: normalizePage(filters.page),
    size: normalizeSize(filters.size),
  }
  const status = filters.status.trim()
  if (status && status !== 'all') {
    query.status = status
  }
  const orderNo = filters.orderNo.trim()
  if (orderNo) {
    query.orderNo = orderNo
  }
  const userId = filters.userId.trim()
  if (userId) {
    query.userId = userId
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

export function buildAdminCancelOrderRequest(requestId: string): AdminCancelOrderRequest {
  return {
    requestId: requestId.trim(),
  }
}

export function getAdminOrderStatusLabel(status: OrderStatus, labels: AdminOrderStatusLabels): string {
  if (status === 'created') {
    return labels.created
  }
  if (status === 'paid') {
    return labels.paid
  }
  if (status === 'cancelled') {
    return labels.cancelled
  }
  if (status === 'shipped' && labels.shipped) {
    return labels.shipped
  }
  if (status === 'completed' && labels.completed) {
    return labels.completed
  }
  return status
}

export function deriveAdminOrderTimeline(
  entries: AdminOrderStatusTimelineResponse[],
  statusLabels: AdminOrderStatusLabels,
  timelineLabels: AdminOrderTimelineLabels,
): AdminOrderTimelineView[] {
  return entries.map((entry) => ({
    status: entry.status,
    statusLabel: getAdminOrderStatusLabel(entry.status, statusLabels),
    occurredAt: entry.occurredAt,
    traceId: entry.traceId,
    description: getAdminOrderTimelineDescription(entry.status, timelineLabels),
  }))
}

export function getAdminOrderTimelineDescription(
  status: OrderStatus,
  labels: AdminOrderTimelineLabels,
): string {
  if (status === 'created') {
    return labels.created
  }
  if (status === 'paid') {
    return labels.paid
  }
  if (status === 'cancelled') {
    return labels.cancelled
  }
  if (status === 'shipped') {
    return labels.shipped
  }
  if (status === 'completed') {
    return labels.completed
  }
  return labels.unknown
}

export function canCancelAdminOrder(status: OrderStatus): boolean {
  return status === 'created'
}

export function toAdminOrderError(
  caught: unknown,
  fallback: string,
  fallbackCode = 'UNEXPECTED_ERROR',
): AdminOrderErrorState {
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

export function readAdminOrderIdFromSearch(search: string): number | null {
  const params = new URLSearchParams(search)
  return parsePositiveInt(params.get('orderId')) ?? null
}

export function readAdminOrderFiltersFromSearch(search: string): AdminOrderFilterDraft | null {
  const params = new URLSearchParams(search)
  const keys = ['status', 'orderNo', 'userId', 'from', 'to', 'page', 'size']
  if (!keys.some((key) => params.has(key))) {
    return null
  }

  return mergeAdminOrderFilters(createDefaultOrderFilters(), {
    status: params.get('status') ?? undefined,
    orderNo: params.get('orderNo') ?? undefined,
    userId: params.get('userId') ?? undefined,
    fromTime: params.get('from') ?? undefined,
    toTime: params.get('to') ?? undefined,
    page: parsePositiveInt(params.get('page')),
    size: parsePositiveInt(params.get('size')),
  })
}

export function buildAdminOrderSearchParams(
  filters: AdminOrderFilterDraft,
  orderId: number | null,
): URLSearchParams {
  const params = new URLSearchParams()
  params.set('workspace', 'order')
  setIfPresent(params, 'status', filters.status === 'all' ? '' : filters.status)
  setIfPresent(params, 'orderNo', filters.orderNo)
  setIfPresent(params, 'userId', filters.userId)
  setIfPresent(params, 'from', filters.fromTime)
  setIfPresent(params, 'to', filters.toTime)
  params.set('page', String(normalizePage(filters.page)))
  params.set('size', String(normalizeSize(filters.size)))
  if (orderId && orderId > 0) {
    params.set('orderId', String(Math.trunc(orderId)))
  }
  return params
}

export function serializeAdminOrderFilters(filters: AdminOrderFilterDraft): string {
  return JSON.stringify({
    version: 1,
    filters: {
      status: filters.status,
      orderNo: filters.orderNo,
      userId: filters.userId,
      fromTime: filters.fromTime,
      toTime: filters.toTime,
      page: normalizePage(filters.page),
      size: normalizeSize(filters.size),
    },
  })
}

export function deserializeAdminOrderFilters(serialized: string | null): AdminOrderFilterDraft | null {
  if (!serialized) {
    return null
  }

  try {
    const parsed = JSON.parse(serialized) as PersistedAdminOrderFilters
    if (parsed.version !== 1 || !parsed.filters) {
      return null
    }
    return mergeAdminOrderFilters(createDefaultOrderFilters(), parsed.filters)
  } catch {
    return null
  }
}

export function mergeAdminOrderFilters(
  defaults: AdminOrderFilterDraft,
  patch: AdminOrderFilterPatch,
): AdminOrderFilterDraft {
  return {
    status: normalizeStatusFilter(patch.status) ?? defaults.status,
    orderNo: patch.orderNo ?? defaults.orderNo,
    userId: patch.userId ?? defaults.userId,
    fromTime: patch.fromTime ?? defaults.fromTime,
    toTime: patch.toTime ?? defaults.toTime,
    page: normalizePage(patch.page ?? defaults.page),
    size: normalizeSize(patch.size ?? defaults.size),
  }
}

export function applyAdminPaymentToDetail(
  detail: AdminOrderDetailResponse | null,
  payment: PaymentResponse,
): AdminOrderDetailResponse | null {
  if (!detail || detail.orderId !== payment.orderId) {
    return detail
  }

  return {
    ...detail,
    paymentNo: payment.paymentNo,
    status: payment.status === 'paid' ? 'paid' : detail.status,
  }
}

export function applyAdminPaymentToSummaries(
  items: AdminOrderSummaryResponse[],
  payment: PaymentResponse,
): AdminOrderSummaryResponse[] {
  return items.map((item) => (
    item.orderId === payment.orderId
      ? {
          ...item,
          paymentNo: payment.paymentNo,
          status: payment.status === 'paid' ? 'paid' : item.status,
        }
      : item
  ))
}

export function createSubmissionGate() {
  let pending = false

  function begin(): boolean {
    if (pending) {
      return false
    }
    pending = true
    return true
  }

  function end() {
    pending = false
  }

  function isPending() {
    return pending
  }

  return {
    begin,
    end,
    isPending,
  }
}

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

function normalizeStatusFilter(value: string | undefined): AdminOrderStatusFilter | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

function setIfPresent(params: URLSearchParams, key: string, value: string) {
  const trimmed = value.trim()
  if (trimmed) {
    params.set(key, trimmed)
  }
}

function parsePositiveInt(value: string | null | undefined): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}
