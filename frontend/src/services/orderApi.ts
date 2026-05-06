import { getJson, postJson } from './httpClient'
import type { CreateOrderRequest, OrderPageResponse, OrderResponse } from '../types/api/order'

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
