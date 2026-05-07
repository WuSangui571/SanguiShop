import { HttpClientError } from '../../services/httpClient'
import type {
  AdminCancelOrderRequest,
  AdminOrderQueryParams,
  AdminOrderStatusFilter,
  OrderStatus,
} from '../../types/api/order'

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
}

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
  return status
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
