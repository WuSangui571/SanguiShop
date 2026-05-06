export interface CreatePaymentRequest {
  shopId: number
  userId: string
  orderId: number
  paymentNo: string
  channel: string
}

export type PaymentStatus = 'created' | 'paid' | 'failed' | string

export interface PaymentResponse {
  paymentId: number
  paymentNo: string
  orderId: number
  orderNo: string
  shopId: number
  userId: string
  channel: string
  status: PaymentStatus
  amountCent: number
}
