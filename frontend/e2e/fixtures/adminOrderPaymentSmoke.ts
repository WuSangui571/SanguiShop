import type { ApiResult } from '../../src/types/api/common'
import type { OpsSessionResponse, PersistedOpsSession } from '../../src/types/api/auth'
import type {
  AdminOrderDetailResponse,
  AdminOrderPageResponse,
  AdminOrderSummaryResponse,
} from '../../src/types/api/order'
import type { PaymentResponse } from '../../src/types/api/payment'

export function apiEnvelope<T>(data: T, code = 'SUCCESS', traceId = 'trace-admin-smoke'): ApiResult<T> {
  return {
    code,
    message: code === 'SUCCESS' ? 'OK' : code,
    data,
    traceId,
    timestamp: '2026-05-16T08:00:00+08:00',
  }
}

export function apiErrorEnvelope(code: string, message: string, traceId: string): ApiResult<unknown> {
  return {
    code,
    message,
    data: null,
    traceId,
    timestamp: '2026-05-16T08:00:00+08:00',
  }
}

export const OPS_SESSION_KEY = 'sangui.ops.session'

export function createOpsSession(overrides?: Partial<PersistedOpsSession>): PersistedOpsSession {
  return {
    userId: overrides?.userId ?? 9001,
    shopId: overrides?.shopId ?? 1,
    username: overrides?.username ?? 'ops-order-admin',
    accessToken: overrides?.accessToken ?? 'mock-ops-jwt-token',
    tokenType: overrides?.tokenType ?? 'Bearer',
    expiresAt: overrides?.expiresAt ?? new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    roles: overrides?.roles ?? [],
    permissions: overrides?.permissions ?? ['ORDER_MANAGEMENT_ADMIN'],
  }
}

export function createOpsCompensationSession(): PersistedOpsSession {
  return createOpsSession({
    username: 'ops-compensation-admin',
    permissions: ['OPS_COMPENSATION_ADMIN'],
  })
}

export function createOpsSessionResponse(session: PersistedOpsSession = createOpsSession()): OpsSessionResponse {
  return {
    userId: session.userId,
    shopId: session.shopId,
    username: session.username,
    accessToken: session.accessToken,
    tokenType: session.tokenType,
    expiresInSeconds: Math.max(1, Math.floor((new Date(session.expiresAt).getTime() - Date.now()) / 1000)),
    roles: [...session.roles],
    permissions: [...session.permissions],
  }
}

export function createAdminOrderSummary(overrides?: Partial<AdminOrderSummaryResponse>): AdminOrderSummaryResponse {
  return {
    orderId: overrides?.orderId ?? 1001,
    orderNo: overrides?.orderNo ?? 'ADM-ORD-1001',
    shopId: overrides?.shopId ?? 1,
    userId: overrides?.userId ?? '10001',
    status: overrides?.status ?? 'created',
    totalAmountCent: overrides?.totalAmountCent ?? 59900,
    paymentNo: overrides?.paymentNo ?? null,
    itemCount: overrides?.itemCount ?? 2,
    traceId: overrides?.traceId ?? 'trace-order-1001',
    createdAt: overrides?.createdAt ?? '2026-05-16T08:00:00+08:00',
    updatedAt: overrides?.updatedAt ?? '2026-05-16T08:00:00+08:00',
  }
}

const DEFAULT_ORDER_ITEMS = [
  {
    productId: 301,
    skuId: 401,
    skuName: 'Daily trainer 42',
    priceCent: 59900,
    quantity: 1,
    lineAmountCent: 59900,
  },
]

function buildTimeline(status: string, orderId: number): Array<{ status: string; occurredAt: string; traceId: string }> {
  const traceId = `trace-order-${orderId}`
  const entries: Array<{ status: string; occurredAt: string; traceId: string }> = [
    { status: 'created', occurredAt: '2026-05-16T08:00:00+08:00', traceId },
  ]
  if (status === 'created') {
    return entries
  }
  entries.push({ status: 'paid', occurredAt: '2026-05-16T08:05:00+08:00', traceId })
  if (status === 'paid') {
    return entries
  }
  entries.push({ status: 'shipped', occurredAt: '2026-05-16T08:10:00+08:00', traceId })
  if (status === 'shipped') {
    return entries
  }
  entries.push({ status: 'completed', occurredAt: '2026-05-16T09:00:00+08:00', traceId })
  if (status === 'completed') {
    return entries
  }
  entries.push({ status: 'cancelled', occurredAt: '2026-05-16T08:10:00+08:00', traceId })
  if (status === 'cancelled') {
    return entries
  }
  entries.push({ status, occurredAt: '2026-05-16T08:10:00+08:00', traceId })
  return entries
}

export function createAdminOrderDetail(overrides?: Partial<AdminOrderDetailResponse>): AdminOrderDetailResponse {
  const orderId = overrides?.orderId ?? 1001
  const status = overrides?.status ?? 'created'
  return {
    orderId,
    orderNo: overrides?.orderNo ?? `ADM-ORD-${orderId}`,
    shopId: overrides?.shopId ?? 1,
    userId: overrides?.userId ?? '10001',
    requestId: overrides?.requestId ?? `req-smoke-${orderId}`,
    reservationNo: overrides?.reservationNo ?? `ord:10001:req-smoke-${orderId}`,
    paymentNo: overrides?.paymentNo ?? null,
    status,
    totalAmountCent: overrides?.totalAmountCent ?? 59900,
    traceId: overrides?.traceId ?? `trace-order-${orderId}`,
    createdAt: overrides?.createdAt ?? '2026-05-16T08:00:00+08:00',
    updatedAt: overrides?.updatedAt ?? '2026-05-16T08:05:00+08:00',
    items: overrides?.items ?? DEFAULT_ORDER_ITEMS,
    statusTimeline: overrides?.statusTimeline ?? buildTimeline(status, orderId),
    ...overrides,
  }
}

export function createAdminOrderPage(orders: AdminOrderSummaryResponse[]): AdminOrderPageResponse {
  return {
    page: 1,
    size: Math.max(orders.length, 20),
    total: orders.length,
    items: orders,
  }
}

export function createAdminPaymentResponse(overrides?: Partial<PaymentResponse>): PaymentResponse {
  return {
    paymentId: overrides?.paymentId ?? 2001,
    paymentNo: overrides?.paymentNo ?? 'ADM-PAY-2001',
    orderId: overrides?.orderId ?? 1001,
    orderNo: overrides?.orderNo ?? 'ADM-ORD-1001',
    shopId: overrides?.shopId ?? 1,
    userId: overrides?.userId ?? '10001',
    channel: overrides?.channel ?? 'mock',
    status: overrides?.status ?? 'paid',
    amountCent: overrides?.amountCent ?? 59900,
  }
}

export function createShippedOrder(): AdminOrderDetailResponse {
  return createAdminOrderDetail({
    orderId: 1003,
    orderNo: 'ADM-SHP-1003',
    status: 'shipped',
    paymentNo: 'ADM-PAY-2003',
    updatedAt: '2026-05-16T08:10:00+08:00',
  })
}

export function createCompletedOrder(): AdminOrderDetailResponse {
  return createAdminOrderDetail({
    orderId: 1004,
    orderNo: 'ADM-CMP-1004',
    status: 'completed',
    paymentNo: 'ADM-PAY-2004',
    updatedAt: '2026-05-16T09:00:00+08:00',
  })
}

export function createCancelledOrder(): AdminOrderDetailResponse {
  return createAdminOrderDetail({
    orderId: 1005,
    orderNo: 'ADM-CNL-1005',
    status: 'cancelled',
    paymentNo: null,
    updatedAt: '2026-05-16T08:10:00+08:00',
    statusTimeline: [
      { status: 'created', occurredAt: '2026-05-16T08:00:00+08:00', traceId: 'trace-order-1005' },
      { status: 'cancelled', occurredAt: '2026-05-16T08:10:00+08:00', traceId: 'trace-order-1005' },
    ],
  })
}

export function createUnknownOrder(): AdminOrderDetailResponse {
  return createAdminOrderDetail({
    orderId: 1006,
    orderNo: 'ADM-UNK-1006',
    status: 'refunding',
    paymentNo: null,
    updatedAt: '2026-05-16T08:10:00+08:00',
    statusTimeline: [
      { status: 'created', occurredAt: '2026-05-16T08:00:00+08:00', traceId: 'trace-order-1006' },
      { status: 'refunding', occurredAt: '2026-05-16T08:10:00+08:00', traceId: 'trace-order-1006' },
    ],
  })
}
