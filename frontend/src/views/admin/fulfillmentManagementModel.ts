import { HttpClientError } from '../../services/httpClient'
import { ref } from 'vue'
import type {
  AdminFulfillmentQueryParams,
  FulfillmentStatus,
  OrderStatus,
  ShipFulfillmentRequest,
} from '../../types/api/order'

export interface FulfillmentErrorState {
  code: string
  message: string
  traceId: string | null
}

export interface FulfillmentFilterDraft {
  status: FulfillmentStatus
  orderNo: string
  userId: string
  fromTime: string
  toTime: string
  page: number
  size: number
}

export interface FulfillmentStatusLabels {
  unshipped: string
  shipped: string
}

export function createDefaultFulfillmentFilters(): FulfillmentFilterDraft {
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

export function buildAdminFulfillmentQuery(filters: FulfillmentFilterDraft): AdminFulfillmentQueryParams {
  const query: AdminFulfillmentQueryParams = {
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

export function buildShipFulfillmentRequest(requestId: string, carrier: string, trackingNo: string): ShipFulfillmentRequest {
  return {
    requestId: requestId.trim(),
    carrier: carrier.trim(),
    trackingNo: trackingNo.trim(),
  }
}

export function getFulfillmentStatusLabel(status: FulfillmentStatus, labels: FulfillmentStatusLabels): string {
  if (status === 'unshipped') {
    return labels.unshipped
  }
  if (status === 'shipped') {
    return labels.shipped
  }
  return status
}

export function canShipFulfillment(fulfillmentStatus: FulfillmentStatus, orderStatus: OrderStatus): boolean {
  return orderStatus === 'paid' && fulfillmentStatus === 'unshipped'
}

export function toFulfillmentError(
  caught: unknown,
  fallback: string,
  fallbackCode = 'UNEXPECTED_ERROR',
): FulfillmentErrorState {
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

export function createShipmentGate() {
  const pending = ref(false)

  function begin(): boolean {
    if (pending.value) {
      return false
    }
    pending.value = true
    return true
  }

  function end() {
    pending.value = false
  }

  function isPending() {
    return pending.value
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
