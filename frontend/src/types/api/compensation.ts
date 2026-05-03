export interface OrderCompensationQueryRequest {
  shopId: number
  orderId?: number
  trigger?: string
  result?: string
  operator?: string
  traceId?: string
  fromTime?: string
  toTime?: string
  pageNo?: number
  pageSize?: number
}

export interface PaymentCompensationQueryRequest {
  shopId: number
  orderId?: number
  paymentNo?: string
  trigger?: string
  result?: string
  operator?: string
  traceId?: string
  fromTime?: string
  toTime?: string
  pageNo?: number
  pageSize?: number
}

export interface OrderCompensationAttemptResponse {
  attemptId: number
  orderId: number
  orderNo: string
  reservationNo: string | null
  result: string
  errorCode: string | null
  reason: string | null
  traceId: string | null
  trigger: string | null
  operator: string | null
  createdAt: string
  updatedAt: string
}

export interface PaymentCompensationAttemptResponse {
  attemptId: number
  paymentId: number
  orderId: number
  paymentNo: string
  orderNo: string
  reservationNo: string | null
  result: string
  errorCode: string | null
  reason: string | null
  traceId: string | null
  trigger: string | null
  operator: string | null
  createdAt: string
  updatedAt: string
}

export interface OrderCompensationRecordResponse {
  orderId: number
  orderNo: string
  userId: string
  reservationNo: string | null
  status: string
  totalAmountCent: number
  traceId: string | null
  createdAt: string
  updatedAt: string
  lastCompensationResult: string | null
  lastCompensationErrorCode: string | null
  lastCompensationReason: string | null
  lastCompensationTraceId: string | null
  lastCompensationTrigger: string | null
  lastCompensationOperator: string | null
  lastCompensatedAt: string | null
}

export interface PaymentCompensationRecordResponse {
  paymentId: number
  paymentNo: string
  orderId: number
  orderNo: string
  userId: string
  channel: string
  status: string
  amountCent: number
  traceId: string | null
  createdAt: string
  updatedAt: string
  lastCompensationResult: string | null
  lastCompensationErrorCode: string | null
  lastCompensationReason: string | null
  lastCompensationTraceId: string | null
  lastCompensationTrigger: string | null
  lastCompensationOperator: string | null
  lastCompensatedAt: string | null
}

export interface OrderCompensationAggregateResponse {
  order: OrderCompensationRecordResponse
  matchedAttemptCount: number
  totalAttemptCount: number
  latestAttemptAt: string | null
  attempts: OrderCompensationAttemptResponse[]
}

export interface PaymentCompensationAggregateResponse {
  payment: PaymentCompensationRecordResponse
  matchedAttemptCount: number
  totalAttemptCount: number
  latestAttemptAt: string | null
  attempts: PaymentCompensationAttemptResponse[]
}

export interface OrderCompensationQueryResponse {
  shopId: number
  pageNo: number
  pageSize: number
  total: number
  items: OrderCompensationAggregateResponse[]
}

export interface PaymentCompensationQueryResponse {
  shopId: number
  pageNo: number
  pageSize: number
  total: number
  items: PaymentCompensationAggregateResponse[]
}
