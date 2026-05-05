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

export interface ManualOrderTimeoutReplayRequest {
  shopId: number
  orderId: number
  timeoutMinutes?: number
  operator: string
}

export interface BulkOrderTimeoutReplayRequest {
  shopId: number
  dryRun: boolean
  operator: string
  timeoutMinutes?: number
  limit: number
  orderIds?: number[]
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

export interface ManualPaymentReconcileRequest {
  shopId: number
  paymentNo: string
  operator: string
}

export interface BulkPaymentReconcileRequest {
  shopId: number
  dryRun: boolean
  operator: string
  minAgeMinutes?: number
  limit: number
  paymentNos?: string[]
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

export interface ManualOrderTimeoutReplayResponse {
  result: string
  errorCode: string | null
  reason: string | null
  order: OrderCompensationRecordResponse
}

export interface BulkOrderTimeoutReplayItemResponse {
  result: string
  errorCode: string | null
  reason: string | null
  order: OrderCompensationRecordResponse
}

export interface BulkOrderTimeoutReplayResponse {
  shopId: number
  dryRun: boolean
  matchedCount: number
  executedCount: number
  successCount: number
  skippedCount: number
  failedCount: number
  items: BulkOrderTimeoutReplayItemResponse[]
}

export interface PaymentCompensationQueryResponse {
  shopId: number
  pageNo: number
  pageSize: number
  total: number
  items: PaymentCompensationAggregateResponse[]
}

export interface ManualPaymentReconcileResponse {
  result: string
  errorCode: string | null
  reason: string | null
  payment: PaymentCompensationRecordResponse
}

export interface BulkPaymentReconcileItemResponse {
  result: string
  errorCode: string | null
  reason: string | null
  payment: PaymentCompensationRecordResponse
}

export interface BulkPaymentReconcileResponse {
  shopId: number
  dryRun: boolean
  matchedCount: number
  executedCount: number
  successCount: number
  skippedCount: number
  failedCount: number
  items: BulkPaymentReconcileItemResponse[]
}
