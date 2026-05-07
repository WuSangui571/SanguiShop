import { getJson, postJson } from './httpClient'
import type {
  AdminFulfillmentPageResponse,
  AdminFulfillmentQueryParams,
  AdminFulfillmentResponse,
  ShipFulfillmentRequest,
} from '../types/api/order'

export function listAdminFulfillments(params: AdminFulfillmentQueryParams) {
  return getJson<AdminFulfillmentPageResponse>('/api/admin/fulfillments', { ...params }, { authContext: 'ops' })
}

export function getAdminFulfillment(orderId: number) {
  return getJson<AdminFulfillmentResponse>(`/api/admin/fulfillments/${encodeURIComponent(String(orderId))}`, {}, {
    authContext: 'ops',
  })
}

export function shipAdminFulfillment(orderId: number, payload: ShipFulfillmentRequest) {
  return postJson<AdminFulfillmentResponse>(
    `/api/admin/fulfillments/${encodeURIComponent(String(orderId))}/ship`,
    payload,
    { authContext: 'ops' },
  )
}
