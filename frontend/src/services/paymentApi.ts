import { getJson, postJson } from './httpClient'
import type { CreatePaymentRequest, PaymentResponse } from '../types/api/payment'

export function createPayment(payload: CreatePaymentRequest) {
  return postJson<PaymentResponse>('/api/payments', payload, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}

export function getPayment(paymentNo: string) {
  return getJson<PaymentResponse>(`/api/payments/${encodeURIComponent(paymentNo)}`, {}, {
    authContext: 'mall',
    suppressAuthStateChange: true,
  })
}
