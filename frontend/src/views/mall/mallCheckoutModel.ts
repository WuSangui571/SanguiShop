import { HttpClientError } from '../../services/httpClient'
import type { MallSession } from '../../types/api/auth'
import type { CreateOrderRequest } from '../../types/api/order'
import type { CreatePaymentRequest } from '../../types/api/payment'
import type { ProductDetailResponse, ProductSkuResponse } from '../../types/api/product'

export interface SubmitOrderState {
  selectedSku: ProductSkuResponse | null
  quantity: number
  isSubmitting: boolean
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

export function describeMallApiError(caught: unknown): string {
  if (caught instanceof HttpClientError) {
    const trace = caught.traceId ? ` Trace ID ${caught.traceId}.` : ''
    return `${caught.code}: ${caught.message}${trace}`
  }

  return 'UNEXPECTED_ERROR: Unexpected request failure.'
}

function createRandomId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }

  return `${Date.now()}-${Math.round(Math.random() * 1_000_000)}`
}
