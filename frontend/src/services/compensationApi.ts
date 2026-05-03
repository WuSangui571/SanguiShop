import { postJson } from './httpClient'
import type {
  OrderCompensationQueryRequest,
  OrderCompensationQueryResponse,
  PaymentCompensationQueryRequest,
  PaymentCompensationQueryResponse,
} from '../types/api/compensation'

export function queryOrderCompensations(payload: OrderCompensationQueryRequest) {
  return postJson<OrderCompensationQueryResponse>('/api/internal/orders/compensation-records/query', payload)
}

export function queryPaymentCompensations(payload: PaymentCompensationQueryRequest) {
  return postJson<PaymentCompensationQueryResponse>('/api/internal/payments/compensation-records/query', payload)
}
