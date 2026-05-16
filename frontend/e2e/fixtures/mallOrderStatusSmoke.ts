import type { ApiResult } from '../../src/types/api/common'
import type { MallLoginResponse } from '../../src/types/api/auth'
import type { OrderResponse, OrderPageResponse, OrderReviewResponse } from '../../src/types/api/order'
import type { PaymentResponse } from '../../src/types/api/payment'
import type {
  ProductDetailResponse,
  ProductListResponse,
  ProductReviewPageResponse,
  ProductSummaryResponse,
} from '../../src/types/api/product'

export function apiEnvelope<T>(data: T, code = 'SUCCESS', traceId = 'trace-smoke'): ApiResult<T> {
  return {
    code,
    message: code === 'SUCCESS' ? 'OK' : code,
    data,
    traceId,
    timestamp: '2026-05-16T08:00:00+08:00',
  }
}

export function apiErrorEnvelope(
  code: string,
  message: string,
  traceId: string,
): ApiResult<unknown> {
  return {
    code,
    message,
    data: undefined as unknown,
    traceId,
    timestamp: '2026-05-16T08:00:00+08:00',
  }
}

export function createLoginResponse(overrides?: Partial<MallLoginResponse>): ApiResult<MallLoginResponse> {
  return apiEnvelope({
    userId: overrides?.userId ?? 10001,
    shopId: overrides?.shopId ?? 1,
    accessToken: overrides?.accessToken ?? 'mock-jwt-token',
    tokenType: overrides?.tokenType ?? 'Bearer',
    expiresInSeconds: overrides?.expiresInSeconds ?? 86400,
    roles: overrides?.roles ?? ['MALL_USER'],
  })
}

export function createOrderResponse(overrides?: Partial<OrderResponse>): OrderResponse {
  return {
    orderId: overrides?.orderId ?? 501,
    orderNo: overrides?.orderNo ?? 'ORD-SMOKE-501',
    shopId: overrides?.shopId ?? 1,
    userId: overrides?.userId ?? '10001',
    requestId: overrides?.requestId ?? 'req-smoke-501',
    status: overrides?.status ?? 'paid',
    totalAmountCent: overrides?.totalAmountCent ?? 59900,
    items: overrides?.items ?? [
      {
        productId: 301,
        skuId: 401,
        skuName: 'Daily trainer 42',
        priceCent: 59900,
        quantity: 1,
        lineAmountCent: 59900,
      },
    ],
    createdAt: overrides?.createdAt ?? '2026-05-16T08:00:00+08:00',
    updatedAt: overrides?.updatedAt ?? '2026-05-16T08:00:00+08:00',
    fulfillmentStatus: overrides?.fulfillmentStatus ?? null,
    carrier: overrides?.carrier ?? null,
    trackingNo: overrides?.trackingNo ?? null,
    shippedAt: overrides?.shippedAt ?? null,
    completedAt: overrides?.completedAt ?? null,
    reviewed: overrides?.reviewed ?? false,
    review: overrides?.review ?? null,
    ...overrides,
  }
}

export function createShippedOrder(): OrderResponse {
  return createOrderResponse({
    orderId: 502,
    orderNo: 'ORD-SMOKE-502',
    status: 'paid',
    fulfillmentStatus: 'shipped',
    carrier: 'SF Express',
    trackingNo: 'SF123456789CN',
    shippedAt: '2026-05-15T12:00:00+08:00',
    updatedAt: '2026-05-16T08:05:00+08:00',
  })
}

export function createCompletedOrder(): OrderResponse {
  return createOrderResponse({
    orderId: 503,
    orderNo: 'ORD-SMOKE-503',
    status: 'completed',
    fulfillmentStatus: 'completed',
    carrier: 'SF Express',
    trackingNo: 'SF987654321CN',
    shippedAt: '2026-05-14T10:00:00+08:00',
    completedAt: '2026-05-15T16:00:00+08:00',
    reviewed: true,
    review: createReviewSnapshot(503),
    updatedAt: '2026-05-16T08:10:00+08:00',
  })
}

export function createCancelledOrder(): OrderResponse {
  return createOrderResponse({
    orderId: 504,
    orderNo: 'ORD-SMOKE-504',
    status: 'cancelled',
    fulfillmentStatus: null,
    updatedAt: '2026-05-16T07:30:00+08:00',
  })
}

export function createUnknownStatusOrder(): OrderResponse {
  return createOrderResponse({
    orderId: 505,
    orderNo: 'ORD-SMOKE-505',
    status: 'refunding',
    fulfillmentStatus: null,
    updatedAt: '2026-05-16T07:45:00+08:00',
  })
}

export function createPaidUnshippedOrder(): OrderResponse {
  return createOrderResponse({
    orderId: 501,
    orderNo: 'ORD-SMOKE-501',
    status: 'paid',
    fulfillmentStatus: 'unshipped',
    updatedAt: '2026-05-16T08:00:00+08:00',
  })
}

function createReviewSnapshot(orderId: number): OrderReviewResponse {
  return {
    orderReviewId: orderId + 4000,
    shopId: 1,
    orderId,
    orderNo: `ORD-SMOKE-${orderId}`,
    userId: '10001',
    rating: 5,
    content: 'Great product.',
    imageUrls: [],
    requestId: 'review-smoke',
    traceId: 'trace-review-smoke',
    createdAt: '2026-05-16T09:00:00+08:00',
  }
}

export function createOrderPage(orders: OrderResponse[]): OrderPageResponse {
  return {
    page: 1,
    size: Math.max(orders.length, 5),
    total: orders.length,
    items: orders,
  }
}

export function createPaymentResponse(overrides?: Partial<PaymentResponse>): PaymentResponse {
  return {
    paymentId: overrides?.paymentId ?? 701,
    paymentNo: overrides?.paymentNo ?? 'PAY-SMOKE-501',
    orderId: overrides?.orderId ?? 501,
    orderNo: overrides?.orderNo ?? 'ORD-SMOKE-501',
    shopId: overrides?.shopId ?? 1,
    userId: overrides?.userId ?? '10001',
    channel: overrides?.channel ?? 'mock',
    status: overrides?.status ?? 'paid',
    amountCent: overrides?.amountCent ?? 59900,
    ...overrides,
  }
}

export function createProductList(): ProductListResponse {
  return {
    items: [createProductSummary()],
    total: 1,
    page: 1,
    size: 12,
  }
}

function createProductSummary(): ProductSummaryResponse {
  return {
    productId: 301,
    productName: 'Daily trainer',
    productDescription: 'Stable shoe for everyday walking.',
    minPriceCent: 59900,
    maxPriceCent: 59900,
    status: 'active',
  }
}

export function createProductDetail(): ProductDetailResponse {
  return {
    productId: 301,
    productName: 'Daily trainer',
    productDescription: 'Stable shoe for everyday walking.',
    status: 'active',
    skus: [
      {
        skuId: 401,
        skuCode: 'shoe-42',
        skuName: '42',
        priceCent: 59900,
        availableStock: 5,
        reservedStock: 0,
      },
      {
        skuId: 402,
        skuCode: 'shoe-43',
        skuName: '43',
        priceCent: 59900,
        availableStock: 3,
        reservedStock: 0,
      },
    ],
  }
}

export function createEmptyReviews(productId = 301): ProductReviewPageResponse {
  return {
    productId,
    averageRating: 0,
    reviewCount: 0,
    ratingDistribution: {},
    page: 1,
    size: 5,
    items: [],
  }
}

export const MALL_SESSION_KEY = 'sangui.mall.session'

export const MOCK_SESSION = {
  userId: 10001,
  shopId: 1,
  accessToken: 'mock-jwt-token',
  tokenType: 'Bearer',
  expiresAt: '2099-12-31T23:59:59+08:00',
  roles: ['MALL_USER'],
}
