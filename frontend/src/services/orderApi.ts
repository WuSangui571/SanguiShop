import { getJson, postJson } from './httpClient'
import type {
  AdminCancelOrderRequest,
  AdminOrderDetailResponse,
  AdminOrderPageResponse,
  AdminOrderQueryParams,
  AdminReviewPageResponse,
  AdminReviewQueryParams,
  AdminReviewVisibilityRequest,
  AdminReviewSummaryResponse,
  ConfirmOrderReceiptRequest,
  CreateOrderRequest,
  CreateOrderReviewRequest,
  OrderPageResponse,
  OrderReviewResponse,
  OrderResponse,
} from '../types/api/order'

export function createOrder(payload: CreateOrderRequest) {
  return postJson<OrderResponse>('/api/orders', payload, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

export function getOrder(orderId: number) {
  return getJson<OrderResponse>(`/api/orders/${encodeURIComponent(String(orderId))}`, {}, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

export function listOrders(params: { page?: number, size?: number } = {}) {
  return getJson<OrderPageResponse>('/api/orders', params, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

export function cancelOrder(orderId: number) {
  return postJson<OrderResponse>(`/api/orders/${encodeURIComponent(String(orderId))}/cancel`, {}, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

export function confirmOrderReceipt(orderId: number, payload: ConfirmOrderReceiptRequest) {
  return postJson<OrderResponse>(
    `/api/orders/${encodeURIComponent(String(orderId))}/receipt-confirmations`,
    payload,
    {
      authContext: 'mall',
      suppressAuthStateChange: true,
    },
  )
}

export function createOrderReview(orderId: number, payload: CreateOrderReviewRequest) {
  return postJson<OrderReviewResponse>(
    `/api/orders/${encodeURIComponent(String(orderId))}/reviews`,
    payload,
    {
      authContext: 'mall',
      suppressAuthStateChange: true,
    },
  )
}

export function getOrderReview(orderId: number) {
  return getJson<OrderReviewResponse | null>(`/api/orders/${encodeURIComponent(String(orderId))}/review`, {}, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

export function listAdminOrders(params: AdminOrderQueryParams) {
  return getJson<AdminOrderPageResponse>('/api/admin/orders', { ...params }, { authContext: 'ops' })
}

export function getAdminOrder(orderId: number) {
  return getJson<AdminOrderDetailResponse>(`/api/admin/orders/${encodeURIComponent(String(orderId))}`, {}, {
    authContext: 'ops',
  })
}

export function cancelAdminOrder(orderId: number, payload: AdminCancelOrderRequest) {
  return postJson<AdminOrderDetailResponse>(
    `/api/admin/orders/${encodeURIComponent(String(orderId))}/cancel`,
    payload,
    { authContext: 'ops' },
  )
}

export function listAdminReviews(params: AdminReviewQueryParams) {
  return getJson<AdminReviewPageResponse>('/api/admin/reviews', { ...params }, { authContext: 'ops' })
}

export function updateAdminReviewVisibility(reviewId: number, payload: AdminReviewVisibilityRequest) {
  return postJson<AdminReviewSummaryResponse>(
    `/api/admin/reviews/${encodeURIComponent(String(reviewId))}/visibility`,
    payload,
    { authContext: 'ops' },
  )
}
