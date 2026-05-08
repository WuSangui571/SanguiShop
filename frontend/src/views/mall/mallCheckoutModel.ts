import { HttpClientError } from '../../services/httpClient'
import type { MallSession } from '../../types/api/auth'
import type { CreateOrderRequest } from '../../types/api/order'
import type { OrderResponse } from '../../types/api/order'
import type { CreatePaymentRequest } from '../../types/api/payment'
import type { PaymentResponse } from '../../types/api/payment'
import type { ProductDetailResponse, ProductSkuResponse } from '../../types/api/product'

export interface SubmitOrderState {
  selectedSku: ProductSkuResponse | null
  quantity: number
  isSubmitting: boolean
}

export type MallPaymentFailureKind =
  | 'auth'
  | 'notPayable'
  | 'duplicatePayment'
  | 'validation'
  | 'system'
  | 'unknown'

export interface MallPaymentFailure {
  kind: MallPaymentFailureKind
  code: string
  message: string
  traceId: string | null
  detail: string
}

export function selectInitialSku(product: ProductDetailResponse): ProductSkuResponse | null {
  return product.skus.find((sku) => sku.availableStock > 0) ?? product.skus[0] ?? null
}

export function canSubmitOrder(state: SubmitOrderState): boolean {
  if (state.isSubmitting || !state.selectedSku) {
    return false
  }

  return state.quantity > 0 && state.quantity <= state.selectedSku.availableStock
}

export function buildCreateOrderRequest(options: {
  session: MallSession
  requestId: string
  skuId: number
  quantity: number
}): CreateOrderRequest {
  return {
    shopId: options.session.shopId,
    userId: String(options.session.userId),
    requestId: options.requestId,
    items: [
      {
        skuId: options.skuId,
        quantity: options.quantity,
      },
    ],
  }
}

export function buildCreatePaymentRequest(options: {
  session: MallSession
  orderId: number
  paymentNo: string
  channel?: string
}): CreatePaymentRequest {
  return {
    shopId: options.session.shopId,
    userId: String(options.session.userId),
    orderId: options.orderId,
    paymentNo: options.paymentNo,
    channel: options.channel ?? 'mock',
  }
}

export function createOrderRequestId(): string {
  return `mall-order-${createRandomId()}`
}

export function createPaymentNo(): string {
  return `PAY-${createRandomId().split('-').join('').toUpperCase()}`
}

export function createReceiptConfirmationRequestId(): string {
  return `mall-receipt-${createRandomId()}`
}

export function createOrderReviewRequestId(): string {
  return `mall-review-${createRandomId()}`
}

export function describeMallApiError(caught: unknown): string {
  if (caught instanceof HttpClientError) {
    const trace = caught.traceId ? ` Trace ID ${caught.traceId}.` : ''
    return `${caught.code}: ${caught.message}${trace}`
  }

  return 'UNEXPECTED_ERROR: Unexpected request failure.'
}

export function classifyMallPaymentFailure(caught: unknown): MallPaymentFailure {
  const detail = describeMallApiError(caught)
  if (!(caught instanceof HttpClientError)) {
    return {
      kind: 'unknown',
      code: 'UNEXPECTED_ERROR',
      message: 'Unexpected request failure.',
      traceId: '',
      detail,
    }
  }

  return {
    kind: resolveMallPaymentFailureKind(caught),
    code: caught.code,
    message: caught.message,
    traceId: caught.traceId,
    detail,
  }
}

export function canCancelOrder(order: OrderResponse | null): boolean {
  return order?.status === 'created'
}

export function describePaymentStatus(order: OrderResponse | null, payment: PaymentResponse | null): string {
  if (payment) {
    return payment.status
  }
  if (order?.status === 'paid') {
    return 'paid'
  }
  if (order?.status === 'cancelled') {
    return 'cancelled'
  }
  if (order?.status === 'created') {
    return 'unpaid'
  }
  return 'unknown'
}

function resolveMallPaymentFailureKind(error: HttpClientError): MallPaymentFailureKind {
  if (
    error.status === 401
    || error.code === 'AUTH_TOKEN_EXPIRED'
    || error.code === 'AUTH_TOKEN_MISSING'
  ) {
    return 'auth'
  }
  if (
    error.code === 'PAYMENT_ORDER_STATUS_INVALID'
    || error.code === 'ORDER_STATUS_INVALID'
    || error.status === 422
  ) {
    return 'notPayable'
  }
  if (
    error.code === 'IDEMPOTENCY_CONFLICT'
    || error.code === 'PAYMENT_DUPLICATE'
    || error.code === 'PAYMENT_ALREADY_EXISTS'
  ) {
    return 'duplicatePayment'
  }
  if (
    error.status === 400
    || error.code === 'VALIDATION_FAILED'
    || error.code === 'PAYMENT_AMOUNT_MISMATCH'
  ) {
    return 'validation'
  }
  if (
    error.status >= 500
    || error.code === 'DOWNSTREAM_TIMEOUT'
    || error.code === 'SERVICE_UNAVAILABLE'
  ) {
    return 'system'
  }
  return 'unknown'
}

function createRandomId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }

  return `${Date.now()}-${Math.round(Math.random() * 1_000_000)}`
}
