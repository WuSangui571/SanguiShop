import { postJson } from './httpClient'
import type { CreateOrderRequest, OrderResponse } from '../types/api/order'

export function createOrder(payload: CreateOrderRequest) {
  return postJson<OrderResponse>('/api/orders', payload, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}
